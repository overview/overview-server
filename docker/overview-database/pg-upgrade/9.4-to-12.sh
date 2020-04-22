#!/usr/bin/env bash

set -m  # enable job control -- so we can run Postgres in the script
set -ex

OLDDIR=/var/lib/postgresql/data
NEWDIR=/var/lib/postgresql/data/12
DUMP_BASEPATH="$OLDDIR/backup-9.4"

cleanup() {
  rm -rf /usr/src/postgresql /opt/postgresql-9.4 /opt/postgresql-12
  for vpackage in .fetch-deps .build-deps .pg-upgrade-rundeps; do
    apk del $vpackage || true
  done
}

# Read /var/lib/postgresl/data as a Postgres 9.4 database, and write to
# /var/lib/postgresql/data/9.4-backup.overview.psql and
# /var/lib/postgresql/data/9.4-backup.overview-dev.psql. Write
# /var/lib/postgresql/data/9.4-backup.psql.success (atomically) on success.
# (Success means, "We don't need to read the old data files ever again.")
dump_9_4() {
  # Logic copied from https://github.com/docker-library/postgres/blob/473b58e971e2eb0351af12288dc4976bd3c591ad/9.4/alpine/Dockerfile
  PG_MAJOR=9.4
  PG_VERSION=9.4.26
  PG_SHA256=f5c014fc4a5c94e8cf11314cbadcade4d84213cfcc82081c9123e1b8847a20b9

  mkdir -p /usr/src/postgresql
  cd /usr/src/postgresql

  apk add --no-cache --virtual .fetch-deps \
    ca-certificates \
    openssl \
    tar

  wget -O postgresql.tar.bz2 "https://ftp.postgresql.org/pub/source/v$PG_VERSION/postgresql-$PG_VERSION.tar.bz2"
  echo "$PG_SHA256 *postgresql.tar.bz2" | sha256sum -c -
  tar \
    --extract \
    --file postgresql.tar.bz2 \
    --directory /usr/src/postgresql \
    --strip-components 1
  rm postgresql.tar.bz2 \

  apk add --no-cache --virtual .build-deps \
    bison \
    coreutils \
    dpkg-dev dpkg \
    flex \
    gcc \
    libc-dev \
    libedit-dev \
    libxml2-dev \
    libxslt-dev \
    linux-headers \
    make \
    openssl-dev \
    perl-utils \
    perl-ipc-run \
    util-linux-dev \
    zlib-dev

  # update "DEFAULT_PGSOCKET_DIR" to "/var/run/postgresql" (matching Debian)
  # see https://anonscm.debian.org/git/pkg-postgresql/postgresql.git/tree/debian/patches/51-default-sockets-in-var.patch?id=8b539fcb3e093a521c095e70bdfa76887217b89f
  awk '$1 == "#define" && $2 == "DEFAULT_PGSOCKET_DIR" && $3 == "\"/tmp\"" { $3 = "\"/var/run/postgresql\""; print; next } { print }' src/include/pg_config_manual.h > src/include/pg_config_manual.h.new
  grep '/var/run/postgresql' src/include/pg_config_manual.h.new
  mv src/include/pg_config_manual.h.new src/include/pg_config_manual.h
  gnuArch="$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)"
  # explicitly update autoconf config.guess and config.sub so they support more arches/libcs
  wget -O config/config.guess 'https://git.savannah.gnu.org/cgit/config.git/plain/config.guess?id=7d3d27baf8107b630586c962c057e22149653deb'
  wget -O config/config.sub 'https://git.savannah.gnu.org/cgit/config.git/plain/config.sub?id=7d3d27baf8107b630586c962c057e22149653deb'
  # configure options taken from:
  # https://anonscm.debian.org/cgit/pkg-postgresql/postgresql.git/tree/debian/rules?h=9.5
  ./configure \
    --build="$gnuArch" \
    --with-uuid=e2fs \
    --with-gnu-ld \
    --with-pgport=5432 \
    --with-system-tzdata=/usr/share/zoneinfo \
    --prefix=/opt/postgresql-9.4 \
    --with-includes=/opt/postgresql-9.4/include \
    --with-libraries=/opt/postgresql-9.4/lib \
    --with-openssl

  make -j "$(nproc)" world
  make install-world
  make -C contrib install

  runDeps="$( \
    scanelf --needed --nobanner --format '%n#p' --recursive /opt/postgresql-9.4 \
      | tr ',' '\n' \
      | sort -u \
      | awk 'system("[ -e /opt/postgresql-9.4/lib/" $1 " ]") == 0 { next } { print "so:" $1 }' \
  )"
  apk add --no-cache --virtual .pg-upgrade-rundeps \
    $runDeps \
    bash \
    su-exec \
    tzdata

  # Start the old Postgres server, run pg_dump, and clean up
  su -l postgres <<EOF
set -ex
/opt/postgresql-9.4/bin/postgres -D $OLDDIR &
PG_PID=\$!
echo "Waiting for Postgres 9.4 to start up"
while ! /opt/postgresql-9.4/bin/psql --list; do
  sleep 1
done
echo "Running pg_dump > $DUMP_BASEPATH.*"
/opt/postgresql-9.4/bin/pg_dump --format=custom overview > $DUMP_BASEPATH.overview.psql
/opt/postgresql-9.4/bin/pg_dump --format=custom overview-dev > $DUMP_BASEPATH.overview-dev.psql
# Don't dump overview-test.psql
touch $DUMP_BASEPATH.success
kill "\$PG_PID"
wait
EOF
  # Start the old Postgres server
  cleanup
}

# Read /var/lib/postgresl/data/9.4-backup.*.psql and write them into a
# Postgres 12 database hosting /var/lib/postgresql/data/12/. Write
# /var/lib/postgresql/data/restore-12.success (atomically) on success.
# (Success means, "we never need to read the backups again.")
restore_12() {
  # https://github.com/docker-library/postgres/blob/6d1f671dcd2c8d51484d19767a33c37110dd650f/12/alpine/Dockerfile
  PG_MAJOR=12
  PG_VERSION=12.2
  PG_SHA256=ad1dcc4c4fc500786b745635a9e1eba950195ce20b8913f50345bb7d5369b5de

  mkdir -p /usr/src/postgresql
  cd /usr/src/postgresql

	apk add --no-cache --virtual .fetch-deps \
		ca-certificates \
		openssl \
		tar

	wget -O postgresql.tar.bz2 "https://ftp.postgresql.org/pub/source/v$PG_VERSION/postgresql-$PG_VERSION.tar.bz2"
	echo "$PG_SHA256 *postgresql.tar.bz2" | sha256sum -c -
	tar \
		--extract \
		--file postgresql.tar.bz2 \
		--directory /usr/src/postgresql \
		--strip-components 1
	rm postgresql.tar.bz2

	apk add --no-cache --virtual .build-deps \
		bison \
		coreutils \
		dpkg-dev dpkg \
		flex \
		gcc \
		libc-dev \
		libedit-dev \
		libxml2-dev \
		libxslt-dev \
		linux-headers \
		llvm9-dev clang g++ \
		make \
		openssl-dev \
		perl-utils \
		perl-ipc-run \
		util-linux-dev \
		zlib-dev \
		icu-dev

  # update "DEFAULT_PGSOCKET_DIR" to "/var/run/postgresql" (matching Debian)
  # see https://anonscm.debian.org/git/pkg-postgresql/postgresql.git/tree/debian/patches/51-default-sockets-in-var.patch?id=8b539fcb3e093a521c095e70bdfa76887217b89f
	awk '$1 == "#define" && $2 == "DEFAULT_PGSOCKET_DIR" && $3 == "\"/tmp\"" { $3 = "\"/var/run/postgresql\""; print; next } { print }' src/include/pg_config_manual.h > src/include/pg_config_manual.h.new
	grep '/var/run/postgresql' src/include/pg_config_manual.h.new
	mv src/include/pg_config_manual.h.new src/include/pg_config_manual.h
	gnuArch="$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)"
  # explicitly update autoconf config.guess and config.sub so they support more arches/libcs
	wget -O config/config.guess 'https://git.savannah.gnu.org/cgit/config.git/plain/config.guess?id=7d3d27baf8107b630586c962c057e22149653deb'
	wget -O config/config.sub 'https://git.savannah.gnu.org/cgit/config.git/plain/config.sub?id=7d3d27baf8107b630586c962c057e22149653deb'
  # configure options taken from:
  # https://anonscm.debian.org/cgit/pkg-postgresql/postgresql.git/tree/debian/rules?h=9.5
	./configure \
		--build="$gnuArch" \
		--enable-integer-datetimes \
		--enable-thread-safety \
		--enable-tap-tests \
		--disable-rpath \
		--with-uuid=e2fs \
		--with-gnu-ld \
		--with-pgport=5432 \
		--with-system-tzdata=/usr/share/zoneinfo \
		--prefix=/opt/postgresql-12 \
		--with-includes=/opt/postgresql-12/include \
		--with-libraries=/opt/postgresql-12/lib \
		--with-openssl \
		--with-libxml \
		--with-libxslt \
		--with-icu \
		--with-llvm
	make -j "$(nproc)" world
	make install-world
	make -C contrib install

	runDeps="$( \
		scanelf --needed --nobanner --format '%n#p' --recursive /opt/postgresql-12 \
			| tr ',' '\n' \
			| sort -u \
			| awk 'system("[ -e /opt/postgresql-12/lib/" $1 " ]") == 0 { next } { print "so:" $1 }' \
	)"
	apk add --no-cache --virtual .pg-upgrade-rundeps \
		$runDeps \
		bash \
		su-exec \
		tzdata

  # Start the new Postgres server, run pg_restore, and clean up
  mkdir /var/lib/postgresql/data/12
  chmod 700 /var/lib/postgresql/data/12
  chown postgres:postgres /var/lib/postgresql/data/12
  su -l postgres -s /bin/bash <<EOF
set -ex
/opt/postgresql-12/bin/initdb -D $NEWDIR --locale=C --encoding=UTF-8 -U postgres
/opt/postgresql-12/bin/postgres -D $NEWDIR &
PG_PID=\$!
echo "Waiting for Postgres 12 to start up"
while ! /opt/postgresql-12/bin/psql --list; do
  sleep 1
done
echo "Running pg_restore < $DUMP_BASEPATH"
/opt/postgresql-12/bin/createuser overview
/opt/postgresql-12/bin/createdb --owner=overview overview
/opt/postgresql-12/bin/createdb --owner=overview overview-dev
/opt/postgresql-12/bin/createdb --owner=overview overview-test

# dumps from pg_dump 9.4 cause this error in pg_restore 12:
# error: could not execute query: ERROR:  schema "public" already exists
# Workaround: avoid the CREATE SCHEMA "public":
# https://www.postgresql.org/message-id/15466-0b90383ff69c6e4b%40postgresql.org

/opt/postgresql-12/bin/pg_restore \\
    -d overview \\
    --use-list <(/opt/postgresql-12/bin/pg_restore --list $DUMP_BASEPATH.overview.psql | grep -v 'SCHEMA - public postgres') \\
    $DUMP_BASEPATH.overview.psql

/opt/postgresql-12/bin/pg_restore \\
    -d overview-dev \\
    --use-list <(/opt/postgresql-12/bin/pg_restore --list $DUMP_BASEPATH.overview-dev.psql | grep -v 'SCHEMA - public postgres') \\
    $DUMP_BASEPATH.overview-dev.psql

touch /var/lib/postgresql/data/restore-12.success
kill "\$PG_PID"
wait
EOF
  cleanup
}

# Clean up from previous run, in case of a problem
cleanup

# Dump to $DUMP_BASEPATH
if test ! -f "$DUMP_BASEPATH".success; then
  dump_9_4
fi

# Restore to /var/lib/postgresql/data/12/
if test ! -f /var/lib/postgresql/data/restore-12.success; then
  restore_12
fi

# Delete all the files we no longer need -- except the ".success" files
# and PG_VERSION.
#
# Deleting the .success files will cause dump_9_4 and restore_12 to run
# again. Deleting PG_VERSION will suggest to the caller that this upgrade
# script should not be run. So we'll delete those files last.
rm -rf /var/lib/postgresql/data/{base,global,pg_*,postgresql.*,*.psql}

# Delete PG_VERSION. Now this script won't run again.
rm /var/lib/postgresql/data/PG_VERSION

# Delete the .success files. (It's no big problem if we leak them.)
rm /var/lib/postgresql/data/*.success
