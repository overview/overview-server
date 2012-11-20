using System;
using System.Text;

public class Windows1252Encoding : Encoding {
  public override string WebName { get { return "windows-1252"; } }

  private char[] map = {
    // Copied from http://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/WindowsBestFit/bestfit1252.txt
    /*0x00*/ '\u0000',  //Null
    /*0x01*/ '\u0001',  //Start Of Heading
    /*0x02*/ '\u0002',  //Start Of Text
    /*0x03*/ '\u0003',  //End Of Text
    /*0x04*/ '\u0004',  //End Of Transmission
    /*0x05*/ '\u0005',  //Enquiry
    /*0x06*/ '\u0006',  //Acknowledge
    /*0x07*/ '\u0007',  //Bell
    /*0x08*/ '\u0008',  //Backspace
    /*0x09*/ '\u0009',  //Horizontal Tabulation
    /*0x0a*/ '\u000a',  //Line Feed
    /*0x0b*/ '\u000b',  //Vertical Tabulation
    /*0x0c*/ '\u000c',  //Form Feed
    /*0x0d*/ '\u000d',  //Carriage Return
    /*0x0e*/ '\u000e',  //Shift Out
    /*0x0f*/ '\u000f',  //Shift In
    /*0x10*/ '\u0010',  //Data Link Escape
    /*0x11*/ '\u0011',  //Device Control One
    /*0x12*/ '\u0012',  //Device Control Two
    /*0x13*/ '\u0013',  //Device Control Three
    /*0x14*/ '\u0014',  //Device Control Four
    /*0x15*/ '\u0015',  //Negative Acknowledge
    /*0x16*/ '\u0016',  //Synchronous Idle
    /*0x17*/ '\u0017',  //End Of Transmission Block
    /*0x18*/ '\u0018',  //Cancel
    /*0x19*/ '\u0019',  //End Of Medium
    /*0x1a*/ '\u001a',  //Substitute
    /*0x1b*/ '\u001b',  //Escape
    /*0x1c*/ '\u001c',  //File Separator
    /*0x1d*/ '\u001d',  //Group Separator
    /*0x1e*/ '\u001e',  //Record Separator
    /*0x1f*/ '\u001f',  //Unit Separator
    /*0x20*/ '\u0020',  //Space
    /*0x21*/ '\u0021',  //Exclamation Mark
    /*0x22*/ '\u0022',  //Quotation Mark
    /*0x23*/ '\u0023',  //Number Sign
    /*0x24*/ '\u0024',  //Dollar Sign
    /*0x25*/ '\u0025',  //Percent Sign
    /*0x26*/ '\u0026',  //Ampersand
    /*0x27*/ '\u0027',  //Apostrophe
    /*0x28*/ '\u0028',  //Left Parenthesis
    /*0x29*/ '\u0029',  //Right Parenthesis
    /*0x2a*/ '\u002a',  //Asterisk
    /*0x2b*/ '\u002b',  //Plus Sign
    /*0x2c*/ '\u002c',  //Comma
    /*0x2d*/ '\u002d',  //Hyphen-Minus
    /*0x2e*/ '\u002e',  //Full Stop
    /*0x2f*/ '\u002f',  //Solidus
    /*0x30*/ '\u0030',  //Digit Zero
    /*0x31*/ '\u0031',  //Digit One
    /*0x32*/ '\u0032',  //Digit Two
    /*0x33*/ '\u0033',  //Digit Three
    /*0x34*/ '\u0034',  //Digit Four
    /*0x35*/ '\u0035',  //Digit Five
    /*0x36*/ '\u0036',  //Digit Six
    /*0x37*/ '\u0037',  //Digit Seven
    /*0x38*/ '\u0038',  //Digit Eight
    /*0x39*/ '\u0039',  //Digit Nine
    /*0x3a*/ '\u003a',  //Colon
    /*0x3b*/ '\u003b',  //Semicolon
    /*0x3c*/ '\u003c',  //Less-Than Sign
    /*0x3d*/ '\u003d',  //Equals Sign
    /*0x3e*/ '\u003e',  //Greater-Than Sign
    /*0x3f*/ '\u003f',  //Question Mark
    /*0x40*/ '\u0040',  //Commercial At
    /*0x41*/ '\u0041',  //Latin Capital Letter A
    /*0x42*/ '\u0042',  //Latin Capital Letter B
    /*0x43*/ '\u0043',  //Latin Capital Letter C
    /*0x44*/ '\u0044',  //Latin Capital Letter D
    /*0x45*/ '\u0045',  //Latin Capital Letter E
    /*0x46*/ '\u0046',  //Latin Capital Letter F
    /*0x47*/ '\u0047',  //Latin Capital Letter G
    /*0x48*/ '\u0048',  //Latin Capital Letter H
    /*0x49*/ '\u0049',  //Latin Capital Letter I
    /*0x4a*/ '\u004a',  //Latin Capital Letter J
    /*0x4b*/ '\u004b',  //Latin Capital Letter K
    /*0x4c*/ '\u004c',  //Latin Capital Letter L
    /*0x4d*/ '\u004d',  //Latin Capital Letter M
    /*0x4e*/ '\u004e',  //Latin Capital Letter N
    /*0x4f*/ '\u004f',  //Latin Capital Letter O
    /*0x50*/ '\u0050',  //Latin Capital Letter P
    /*0x51*/ '\u0051',  //Latin Capital Letter Q
    /*0x52*/ '\u0052',  //Latin Capital Letter R
    /*0x53*/ '\u0053',  //Latin Capital Letter S
    /*0x54*/ '\u0054',  //Latin Capital Letter T
    /*0x55*/ '\u0055',  //Latin Capital Letter U
    /*0x56*/ '\u0056',  //Latin Capital Letter V
    /*0x57*/ '\u0057',  //Latin Capital Letter W
    /*0x58*/ '\u0058',  //Latin Capital Letter X
    /*0x59*/ '\u0059',  //Latin Capital Letter Y
    /*0x5a*/ '\u005a',  //Latin Capital Letter Z
    /*0x5b*/ '\u005b',  //Left Square Bracket
    /*0x5c*/ '\u005c',  //Reverse Solidus
    /*0x5d*/ '\u005d',  //Right Square Bracket
    /*0x5e*/ '\u005e',  //Circumflex Accent
    /*0x5f*/ '\u005f',  //Low Line
    /*0x60*/ '\u0060',  //Grave Accent
    /*0x61*/ '\u0061',  //Latin Small Letter A
    /*0x62*/ '\u0062',  //Latin Small Letter B
    /*0x63*/ '\u0063',  //Latin Small Letter C
    /*0x64*/ '\u0064',  //Latin Small Letter D
    /*0x65*/ '\u0065',  //Latin Small Letter E
    /*0x66*/ '\u0066',  //Latin Small Letter F
    /*0x67*/ '\u0067',  //Latin Small Letter G
    /*0x68*/ '\u0068',  //Latin Small Letter H
    /*0x69*/ '\u0069',  //Latin Small Letter I
    /*0x6a*/ '\u006a',  //Latin Small Letter J
    /*0x6b*/ '\u006b',  //Latin Small Letter K
    /*0x6c*/ '\u006c',  //Latin Small Letter L
    /*0x6d*/ '\u006d',  //Latin Small Letter M
    /*0x6e*/ '\u006e',  //Latin Small Letter N
    /*0x6f*/ '\u006f',  //Latin Small Letter O
    /*0x70*/ '\u0070',  //Latin Small Letter P
    /*0x71*/ '\u0071',  //Latin Small Letter Q
    /*0x72*/ '\u0072',  //Latin Small Letter R
    /*0x73*/ '\u0073',  //Latin Small Letter S
    /*0x74*/ '\u0074',  //Latin Small Letter T
    /*0x75*/ '\u0075',  //Latin Small Letter U
    /*0x76*/ '\u0076',  //Latin Small Letter V
    /*0x77*/ '\u0077',  //Latin Small Letter W
    /*0x78*/ '\u0078',  //Latin Small Letter X
    /*0x79*/ '\u0079',  //Latin Small Letter Y
    /*0x7a*/ '\u007a',  //Latin Small Letter Z
    /*0x7b*/ '\u007b',  //Left Curly Bracket
    /*0x7c*/ '\u007c',  //Vertical Line
    /*0x7d*/ '\u007d',  //Right Curly Bracket
    /*0x7e*/ '\u007e',  //Tilde
    /*0x7f*/ '\u007f',  //Delete
    /*0x80*/ '\u20ac',  //Euro Sign
    /*0x81*/ '\u0081',
    /*0x82*/ '\u201a',  //Single Low-9 Quotation Mark
    /*0x83*/ '\u0192',  //Latin Small Letter F With Hook
    /*0x84*/ '\u201e',  //Double Low-9 Quotation Mark
    /*0x85*/ '\u2026',  //Horizontal Ellipsis
    /*0x86*/ '\u2020',  //Dagger
    /*0x87*/ '\u2021',  //Double Dagger
    /*0x88*/ '\u02c6',  //Modifier Letter Circumflex Accent
    /*0x89*/ '\u2030',  //Per Mille Sign
    /*0x8a*/ '\u0160',  //Latin Capital Letter S With Caron
    /*0x8b*/ '\u2039',  //Single Left-Pointing Angle Quotation Mark
    /*0x8c*/ '\u0152',  //Latin Capital Ligature Oe
    /*0x8d*/ '\u008d',
    /*0x8e*/ '\u017d',  //Latin Capital Letter Z With Caron
    /*0x8f*/ '\u008f',
    /*0x90*/ '\u0090',
    /*0x91*/ '\u2018',  //Left Single Quotation Mark
    /*0x92*/ '\u2019',  //Right Single Quotation Mark
    /*0x93*/ '\u201c',  //Left Double Quotation Mark
    /*0x94*/ '\u201d',  //Right Double Quotation Mark
    /*0x95*/ '\u2022',  //Bullet
    /*0x96*/ '\u2013',  //En Dash
    /*0x97*/ '\u2014',  //Em Dash
    /*0x98*/ '\u02dc',  //Small Tilde
    /*0x99*/ '\u2122',  //Trade Mark Sign
    /*0x9a*/ '\u0161',  //Latin Small Letter S With Caron
    /*0x9b*/ '\u203a',  //Single Right-Pointing Angle Quotation Mark
    /*0x9c*/ '\u0153',  //Latin Small Ligature Oe
    /*0x9d*/ '\u009d',
    /*0x9e*/ '\u017e',  //Latin Small Letter Z With Caron
    /*0x9f*/ '\u0178',  //Latin Capital Letter Y With Diaeresis
    /*0xa0*/ '\u00a0',  //No-Break Space
    /*0xa1*/ '\u00a1',  //Inverted Exclamation Mark
    /*0xa2*/ '\u00a2',  //Cent Sign
    /*0xa3*/ '\u00a3',  //Pound Sign
    /*0xa4*/ '\u00a4',  //Currency Sign
    /*0xa5*/ '\u00a5',  //Yen Sign
    /*0xa6*/ '\u00a6',  //Broken Bar
    /*0xa7*/ '\u00a7',  //Section Sign
    /*0xa8*/ '\u00a8',  //Diaeresis
    /*0xa9*/ '\u00a9',  //Copyright Sign
    /*0xaa*/ '\u00aa',  //Feminine Ordinal Indicator
    /*0xab*/ '\u00ab',  //Left-Pointing Double Angle Quotation Mark
    /*0xac*/ '\u00ac',  //Not Sign
    /*0xad*/ '\u00ad',  //Soft Hyphen
    /*0xae*/ '\u00ae',  //Registered Sign
    /*0xaf*/ '\u00af',  //Macron
    /*0xb0*/ '\u00b0',  //Degree Sign
    /*0xb1*/ '\u00b1',  //Plus-Minus Sign
    /*0xb2*/ '\u00b2',  //Superscript Two
    /*0xb3*/ '\u00b3',  //Superscript Three
    /*0xb4*/ '\u00b4',  //Acute Accent
    /*0xb5*/ '\u00b5',  //Micro Sign
    /*0xb6*/ '\u00b6',  //Pilcrow Sign
    /*0xb7*/ '\u00b7',  //Middle Dot
    /*0xb8*/ '\u00b8',  //Cedilla
    /*0xb9*/ '\u00b9',  //Superscript One
    /*0xba*/ '\u00ba',  //Masculine Ordinal Indicator
    /*0xbb*/ '\u00bb',  //Right-Pointing Double Angle Quotation Mark
    /*0xbc*/ '\u00bc',  //Vulgar Fraction One Quarter
    /*0xbd*/ '\u00bd',  //Vulgar Fraction One Half
    /*0xbe*/ '\u00be',  //Vulgar Fraction Three Quarters
    /*0xbf*/ '\u00bf',  //Inverted Question Mark
    /*0xc0*/ '\u00c0',  //Latin Capital Letter A With Grave
    /*0xc1*/ '\u00c1',  //Latin Capital Letter A With Acute
    /*0xc2*/ '\u00c2',  //Latin Capital Letter A With Circumflex
    /*0xc3*/ '\u00c3',  //Latin Capital Letter A With Tilde
    /*0xc4*/ '\u00c4',  //Latin Capital Letter A With Diaeresis
    /*0xc5*/ '\u00c5',  //Latin Capital Letter A With Ring Above
    /*0xc6*/ '\u00c6',  //Latin Capital Ligature Ae
    /*0xc7*/ '\u00c7',  //Latin Capital Letter C With Cedilla
    /*0xc8*/ '\u00c8',  //Latin Capital Letter E With Grave
    /*0xc9*/ '\u00c9',  //Latin Capital Letter E With Acute
    /*0xca*/ '\u00ca',  //Latin Capital Letter E With Circumflex
    /*0xcb*/ '\u00cb',  //Latin Capital Letter E With Diaeresis
    /*0xcc*/ '\u00cc',  //Latin Capital Letter I With Grave
    /*0xcd*/ '\u00cd',  //Latin Capital Letter I With Acute
    /*0xce*/ '\u00ce',  //Latin Capital Letter I With Circumflex
    /*0xcf*/ '\u00cf',  //Latin Capital Letter I With Diaeresis
    /*0xd0*/ '\u00d0',  //Latin Capital Letter Eth
    /*0xd1*/ '\u00d1',  //Latin Capital Letter N With Tilde
    /*0xd2*/ '\u00d2',  //Latin Capital Letter O With Grave
    /*0xd3*/ '\u00d3',  //Latin Capital Letter O With Acute
    /*0xd4*/ '\u00d4',  //Latin Capital Letter O With Circumflex
    /*0xd5*/ '\u00d5',  //Latin Capital Letter O With Tilde
    /*0xd6*/ '\u00d6',  //Latin Capital Letter O With Diaeresis
    /*0xd7*/ '\u00d7',  //Multiplication Sign
    /*0xd8*/ '\u00d8',  //Latin Capital Letter O With Stroke
    /*0xd9*/ '\u00d9',  //Latin Capital Letter U With Grave
    /*0xda*/ '\u00da',  //Latin Capital Letter U With Acute
    /*0xdb*/ '\u00db',  //Latin Capital Letter U With Circumflex
    /*0xdc*/ '\u00dc',  //Latin Capital Letter U With Diaeresis
    /*0xdd*/ '\u00dd',  //Latin Capital Letter Y With Acute
    /*0xde*/ '\u00de',  //Latin Capital Letter Thorn
    /*0xdf*/ '\u00df',  //Latin Small Letter Sharp S
    /*0xe0*/ '\u00e0',  //Latin Small Letter A With Grave
    /*0xe1*/ '\u00e1',  //Latin Small Letter A With Acute
    /*0xe2*/ '\u00e2',  //Latin Small Letter A With Circumflex
    /*0xe3*/ '\u00e3',  //Latin Small Letter A With Tilde
    /*0xe4*/ '\u00e4',  //Latin Small Letter A With Diaeresis
    /*0xe5*/ '\u00e5',  //Latin Small Letter A With Ring Above
    /*0xe6*/ '\u00e6',  //Latin Small Ligature Ae
    /*0xe7*/ '\u00e7',  //Latin Small Letter C With Cedilla
    /*0xe8*/ '\u00e8',  //Latin Small Letter E With Grave
    /*0xe9*/ '\u00e9',  //Latin Small Letter E With Acute
    /*0xea*/ '\u00ea',  //Latin Small Letter E With Circumflex
    /*0xeb*/ '\u00eb',  //Latin Small Letter E With Diaeresis
    /*0xec*/ '\u00ec',  //Latin Small Letter I With Grave
    /*0xed*/ '\u00ed',  //Latin Small Letter I With Acute
    /*0xee*/ '\u00ee',  //Latin Small Letter I With Circumflex
    /*0xef*/ '\u00ef',  //Latin Small Letter I With Diaeresis
    /*0xf0*/ '\u00f0',  //Latin Small Letter Eth
    /*0xf1*/ '\u00f1',  //Latin Small Letter N With Tilde
    /*0xf2*/ '\u00f2',  //Latin Small Letter O With Grave
    /*0xf3*/ '\u00f3',  //Latin Small Letter O With Acute
    /*0xf4*/ '\u00f4',  //Latin Small Letter O With Circumflex
    /*0xf5*/ '\u00f5',  //Latin Small Letter O With Tilde
    /*0xf6*/ '\u00f6',  //Latin Small Letter O With Diaeresis
    /*0xf7*/ '\u00f7',  //Division Sign
    /*0xf8*/ '\u00f8',  //Latin Small Letter O With Stroke
    /*0xf9*/ '\u00f9',  //Latin Small Letter U With Grave
    /*0xfa*/ '\u00fa',  //Latin Small Letter U With Acute
    /*0xfb*/ '\u00fb',  //Latin Small Letter U With Circumflex
    /*0xfc*/ '\u00fc',  //Latin Small Letter U With Diaeresis
    /*0xfd*/ '\u00fd',  //Latin Small Letter Y With Acute
    /*0xfe*/ '\u00fe',  //Latin Small Letter Thorn
    /*0xff*/ '\u00ff'  //Latin Small Letter Y With Diaeresis
  };

  public override int GetMaxByteCount(int charCount) { return charCount; }
  public override int GetMaxCharCount(int byteCount) { return byteCount; }

  public override int GetByteCount(char[] chars, int index, int count) {
    if (chars == null) throw new ArgumentNullException("chars");
    if (index < 0 || index > chars.Length) throw new ArgumentOutOfRangeException("index");
    if (count < 0 || index + count > chars.Length) throw new ArgumentOutOfRangeException("count");
    return count;
  }

  public override int GetCharCount(byte[] bytes, int index, int count) {
    if (bytes == null) throw new ArgumentNullException("bytes");
    if (index < 0 || index > bytes.Length) throw new ArgumentOutOfRangeException("index");
    if (count < 0 || index + count > bytes.Length) throw new ArgumentOutOfRangeException("count");
    return count;
  }

  public override int GetBytes(char[] chars, int charIndex, int charCount, byte[] bytes, int byteIndex) {
    if (chars == null) throw new ArgumentNullException("chars");
    if (bytes == null) throw new ArgumentNullException("bytes");
    if (charIndex < 0) throw new ArgumentOutOfRangeException("charIndex");
    if (charCount < 0) throw new ArgumentOutOfRangeException("charCount");
    if (byteIndex < 0) throw new ArgumentOutOfRangeException("byteIndex");
    if (charIndex + charCount > chars.Length) throw new ArgumentOutOfRangeException("charCount");
    if (byteIndex + charCount > bytes.Length) throw new ArgumentException("bytes");

    int end = charIndex + charCount;

    for (int curIn = charIndex, curOut = byteIndex; curIn < end; curIn++, curOut++) {
      char curChar = chars[curIn];
      byte curByte;
      switch (curChar) {
        case '\u20ac': curByte = 0x80; break; //Euro Sign
        case '\u201a': curByte = 0x82; break; //Single Low-9 Quotation Mark
        case '\u0192': curByte = 0x83; break; //Latin Small Letter F With Hook
        case '\u201e': curByte = 0x84; break; //Double Low-9 Quotation Mark
        case '\u2026': curByte = 0x85; break; //Horizontal Ellipsis
        case '\u2020': curByte = 0x86; break; //Dagger
        case '\u2021': curByte = 0x87; break; //Double Dagger
        case '\u02c6': curByte = 0x88; break; //Modifier Letter Circumflex Accent
        case '\u2030': curByte = 0x89; break; //Per Mille Sign
        case '\u0160': curByte = 0x8a; break; //Latin Capital Letter S With Caron
        case '\u2039': curByte = 0x8b; break; //Single Left-Pointing Angle Quotation Mark
        case '\u0152': curByte = 0x8c; break; //Latin Capital Ligature Oe
        case '\u017d': curByte = 0x8e; break; //Latin Capital Letter Z With Caron
        case '\u2018': curByte = 0x91; break; //Left Single Quotation Mark
        case '\u2019': curByte = 0x92; break; //Right Single Quotation Mark
        case '\u201c': curByte = 0x93; break; //Left Double Quotation Mark
        case '\u201d': curByte = 0x94; break; //Right Double Quotation Mark
        case '\u2022': curByte = 0x95; break; //Bullet
        case '\u2013': curByte = 0x96; break; //En Dash
        case '\u2014': curByte = 0x97; break; //Em Dash
        case '\u02dc': curByte = 0x98; break; //Small Tilde
        case '\u2122': curByte = 0x99; break; //Trade Mark Sign
        case '\u0161': curByte = 0x9a; break; //Latin Small Letter S With Caron
        case '\u203a': curByte = 0x9b; break; //Single Right-Pointing Angle Quotation Mark
        case '\u0153': curByte = 0x9c; break; //Latin Small Ligature Oe
        case '\u017e': curByte = 0x9e; break; //Latin Small Letter Z With Caron
        case '\u0178': curByte = 0x9f; break; //Latin Capital Letter Y With Diaeresis
        default:
          if (curChar <= 0xff) {
            curByte = (byte) curChar;
          } else {
            curByte = (byte) 0x1a;
          }
          break;
      }

      bytes[curOut] = curByte;
    }

    return charCount;
  }

  public override int GetChars(byte[] bytes, int byteIndex, int byteCount, char[] chars, int charIndex) {
    if (bytes == null) throw new ArgumentNullException("bytes");
    if (chars == null) throw new ArgumentNullException("chars");
    if (byteIndex < 0) throw new ArgumentOutOfRangeException("byteIndex");
    if (byteCount < 0) throw new ArgumentOutOfRangeException("byteCount");
    if (charIndex < 0) throw new ArgumentOutOfRangeException("charIndex");
    if (byteIndex + byteCount > bytes.Length) throw new ArgumentOutOfRangeException("byteCount");
    if (charIndex + byteCount > chars.Length) throw new ArgumentException("chars");

    int end = byteIndex + byteCount;

    for (int curIn = byteIndex, curOut = charIndex; curIn < end; curIn++, curOut++) {
      byte curByte = bytes[curIn];
      char curChar = map[curByte];
      chars[curOut] = curChar;
    }

    return byteCount;
  }
}
