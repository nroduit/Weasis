/**
* 
* Copyright (C) 2004-2008 FhG Fokus
*
* This file is part of the FhG Fokus UPnP stack - an open source UPnP implementation
* with some additional features
*
* You can redistribute the FhG Fokus UPnP stack and/or modify it
* under the terms of the GNU General Public License Version 3 as published by
* the Free Software Foundation.
*
* For a license to use the FhG Fokus UPnP stack software under conditions
* other than those described here, or to purchase support for this
* software, please contact Fraunhofer FOKUS by e-mail at the following
* addresses:
*   upnpstack@fokus.fraunhofer.de
*
* The FhG Fokus UPnP stack is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, see <http://www.gnu.org/licenses/>
* or write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
*
*/

package org.weasis.core.ui.model.utils;

/**
 * This class provides methods to convert numbers into byte arrays and vice versa.
 * 
 * @author Alexander Koenig
 * 
 * 
 */
public class ByteArrayHelper
{

  /** Fills a byte array with a value. */
  public static void fill(byte[] array, byte value)
  {
    if (array == null)
    {
      return;
    }
    for (int i = 0; i < array.length; i++)
    {
      array[i] = value;
    }
  }

  /** Fills a byte array with 0. */
  public static void clear(byte[] array)
  {
    fill(array, (byte)0);
  }

  /** Compares the content of two byte arrays. Returns true if both arrays are null or have exactly the same content. */
  public static boolean isEqual(byte[] array1, byte[] array2)
  {
    if (array1 == null && array2 == null)
    {
      return true;
    }
    if (array1 == null || array2 == null)
    {
      return false;
    }
    if (array1.length != array2.length)
    {
      return false;
    }

    for (int i = 0; i < array1.length; i++)
    {
      if (array1[i] != array2[i])
      {
        return false;
      }
    }
    return true;
  }

  /** Converts an uint8 into a 1 byte array */
  public static byte[] uint8ToByteArray(short value)
  {
    return new byte[] {
      (byte)(value & 0xFF)
    };
  }

  /** Converts an int8 into a 1 byte array */
  public static byte[] int8ToByteArray(byte value)
  {
    return new byte[] {
      value
    };
  }

  /** Converts an uint16 into a 2 byte array */
  public static byte[] uint16ToByteArray(int value)
  {
    return new byte[] {
        (byte)(value >> 8 & 0xFF), (byte)(value & 0xFF)
    };
  }

  /** Converts an int16 into a 2 byte array */
  public static byte[] int16ToByteArray(short value)
  {
    return new byte[] {
        (byte)(value >> 8 & 0xFF), (byte)(value & 0xFF)
    };
  }

  /** Converts an uint32 into a 4 byte array */
  public static byte[] uint32ToByteArray(long value)
  {
    return new byte[] {
        (byte)(value >> 24 & 0xFF), (byte)(value >> 16 & 0xFF), (byte)(value >> 8 & 0xFF), (byte)(value >> 0 & 0xFF)
    };
  }

  /** Converts an int32 into a 4 byte array */
  public static byte[] int32ToByteArray(int value)
  {
    return new byte[] {
        (byte)(value >> 24 & 0xFF), (byte)(value >> 16 & 0xFF), (byte)(value >> 8 & 0xFF), (byte)(value >> 0 & 0xFF)
    };
  }

  /**
   * Converts an int64 into a byte array with a maximum size of 8. Leading digits are filled with zero.
   */
  public static byte[] int64ToByteArray(long value, int size)
  {
    if (size > 8)
    {
      return null;
    }
    byte[] tempResult = new byte[8];
    clear(tempResult);

    int offset = 7;
    while (value != 0 && offset != -1)
    {
      tempResult[offset] = (byte)(value & 0xFF);
      value = value >> 8;
      offset--;
    }
    byte[] result = new byte[size];
    System.arraycopy(tempResult, 8 - size, result, 0, size);

    return result;
  }

  /** Converts a 2-byte array into an uint16 */
  public static int byteArrayToUInt16(byte[] data, int offset)
  {
    if (data == null || data.length < offset + 2)
    {
      return 0;
    }
    int result = ((data[offset] & 0xFF) << 8) + (data[offset + 1] & 0xFF);

    return result;
  }

  /** Converts a 2-byte array into an int16 */
  public static short byteArrayToInt16(byte[] data, int offset)
  {
    return (short)byteArrayToUInt16(data, offset);
  }

  /** Converts a 4-byte array into an uint32 */
  public static long byteArrayToUInt32(byte[] data, int offset)
  {
    if (data == null || data.length < offset + 4)
    {
      return 0;
    }
    long result =
      ((data[offset] & 0xFF) << 24) + ((data[offset + 1] & 0xFF) << 16) + ((data[offset + 2] & 0xFF) << 8) +
        (data[offset + 3] & 0xFF);

    return result;
  }

  /** Converts a 4-byte array into an int32 */
  public static int byteArrayToInt32(byte[] data, int offset)
  {
    return (int)byteArrayToUInt32(data, offset);
  }

  /** Converts a byte array into a long value */
  public static long byteArrayToInt64(byte[] data, int offset, int length)
  {
    if (data == null || offset + length > data.length)
    {
      return 0;
    }
    long result = 0;
    for (int i = 0; i < length; i++)
    {
      result = (result << 8) + (data[offset + i] & 0xFF);
    }
    return result;
  }

  public static byte[] reverse(byte[] array) {
    if (array == null) {
      return null;
    }
    byte[] returnArray = array.clone();
    int i = 0;
    int j = returnArray.length - 1;
    byte tmp;
    while (j > i) {
      tmp = returnArray[j];
      returnArray[j] = returnArray[i];
      returnArray[i] = tmp;
      j--;
      i++;
    }
    return returnArray;
  }

}
