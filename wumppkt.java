// The following implements the packet formats for BUMP and HUMP.
// Each individual packet type derives from class BASE, containing
// protocol and opcode fields only; we don't really use this inheritance
// hierarchy though.

// packets can be constructed with the applicable constructors;
// each ctor requires parameters for the necessary fields.
// when possible, there is a "convenience" ctor setting proto = BUMPPROTO.

// The "raw" packet format, as sent and received via DatagramSocket,
// is byte[].  Packets (at least those that one might *receive*)
// can be constructed from a byte[]. For DATA packets, we also need
// to specify the length of the packet, not necessarily the same as
// the length of the byte[] buffer.

// All packet classes also have a write() member function that
// writes out the packet fields into a byte[], for sending.

//import java.lang.*;     //pld
//import java.net.*;      //pld
//import java.lang.System.*;
import java.io.*;

public class wumppkt {

    public static final short BUMPPROTO = 1;
    public static final short HUMPPROTO = 2;
    public static final short CHUMPPROTO= 3;

    public static short THEPROTO = BUMPPROTO;

    public static final short REQop = 1;
    public static final short DATAop= 2;
    public static final short ACKop = 3;
    public static final short ERRORop=4;
    public static final short HANDOFFop=5;

    public static final short SERVERPORT = 4715;
    public static final short SAMEPORT   = 4716;

    public static final int   INITTIMEOUT = 3000;   // milliseconds
    public static final int   SHORTSIZE = 2;            // in bytes
    public static final int   INTSIZE   = 4;
    public static final int   BASESIZE  = 2;
    public static final int   MAXDATASIZE=512;
    public static final int   DHEADERSIZE = BASESIZE + SHORTSIZE + INTSIZE; // DATA header size
    public static final int   EHEADERSIZE = BASESIZE + SHORTSIZE;
    public static final int   MAXSIZE  = DHEADERSIZE + MAXDATASIZE;

    public static final int   EBADPORT  =1;  /* packet from wrong port */
    public static final int   EBADPROTO =2;  /* unknown protocol */
    public static final int   EBADOPCODE=3;  /* unknown opcode */
    public static final int   ENOFILE   =4;  /* REQ for nonexistent file */
    public static final int   ENOPERM   =5;  /* REQ for file with wrong permissions */


static int proto(byte[] buf) {
    return  buf[0];
}

static int opcode(byte[] buf) {
    return buf[1];
}

    private static void w_assert(boolean cond, String s) {
        if (cond) return;
        System.err.println("assertion failed: " + s);
        java.lang.System.exit(1);
    }

static public void setproto(short proto) {
	w_assert(proto == BUMPPROTO || proto == HUMPPROTO, "unsupported protocol: " + proto);	// only supported ones
	THEPROTO = proto;
}

//************************************************************************

public static class BASE { //implements Externalizable {
// don't construct these unless the buffer has length >=4!

// the data:
    private byte  _proto;
    private byte  _opcode;

    //---------------------------------

    public BASE(int proto, int opcode) {
        //super();              // call to base ctor
        _proto = (byte) proto;
        _opcode = (byte) opcode;
    }

    public BASE(byte[] buf) {        // constructs pkt out of packetbuf
    }

    public BASE() {}                 // packet ctors do all the work!

    public byte[] write() {         // not used
        return null;
    }

    public int size() {
        return BASESIZE;
    }

    public int proto() {return _proto;}
    public int opcode() {return _opcode;}
}

//*******************

public static class REQ extends BASE {

    private short  _winsize;
    private String _filename;

    //---------------------------------

    public REQ(int proto, int winsize, String filename) {
        super(proto, REQop);
        _winsize = (short) winsize;
        _filename = filename;
    }

    public REQ(int winsize, String filename) {
        this(THEPROTO, winsize, filename);
    }

    public REQ(byte[] buf) {            // not complete but not needed
        //super(BUMPPROTO, REQop);
    }

    public byte[] write() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            //writeExternal(dos);
            dos.writeByte(super.proto());
            dos.writeByte(super.opcode());
            dos.writeShort(_winsize);
            dos.writeBytes(_filename);
            dos.writeByte(0);
            return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("BASE packet output conversion failed");
                return null;
            }
        //return null;
    }

    public int size() {
        return super.size() + SHORTSIZE + _filename.length() + 1;
    }

    public String filename() {return _filename;}
}

//*******************

public static class HANDOFF extends BASE {

    private int _newport;

   //---------------------------------

    public HANDOFF(byte[] buf) {
	this(THEPROTO, buf);
    }

    // ctor for building a HANDOFF out of incoming buffer:
    public HANDOFF(int proto, byte[] buf) {
        super(proto, HANDOFFop);
        w_assert(buf.length >= BASESIZE + SHORTSIZE, "bad HANDOFF pkt size of " + buf.length);
        ByteArrayInputStream bais = new ByteArrayInputStream(buf, 0, buf.length);
        DataInputStream dis = new DataInputStream(bais);
        try {
            int p = dis.readByte();
            w_assert(p==proto, "Expecting proto " + proto + ", got " + p);
            int o = dis.readByte();
            w_assert(o==HANDOFFop, "Expecting opcode=HANDOFF, got " + o);
            _newport=dis.readUnsignedShort();

        } catch (IOException ioe) {
            System.err.println("HANDOFF packet conversion failed");
            return;
        }
    }

    public int newport() {return _newport;}

    // for building a HANDOFF to send; server-side only, may not work!
    public byte[] write() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            //writeExternal(dos);
            dos.writeByte(super.proto());
            dos.writeByte(super.opcode());
            dos.writeShort(_newport);
            return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("HANDOFF packet output conversion failed");
                return null;
            }
     }
     /* */

    public int size() {
        return super.size() + SHORTSIZE;
    }

}

//*******************

public static class ACK extends BASE {

    private int _blocknum;

    //---------------------------------

    public ACK(int blocknum) {
        this(THEPROTO, blocknum);
    }

    public ACK(short proto, int blocknum) {
        super(proto, ACKop);
        _blocknum = blocknum;
    }

    public int blocknum() {return _blocknum;}
    public void setblock(int blocknum) {_blocknum = blocknum;}

    public byte[] write() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            //writeExternal(dos);
            dos.writeByte(super.proto());
            dos.writeByte(super.opcode());
            dos.writeShort(0);          // padding
            dos.writeInt(_blocknum);
            return baos.toByteArray();
            } catch (IOException ioe) {
                System.err.println("ACK packet output conversion failed");
                return null;
            }
     }

    public int size() {
        return super.size() + SHORTSIZE + INTSIZE;
    }

    public ACK(byte[] buf) {}       // not complete but not needed
}

//*******************

public static class DATA extends BASE {

    private int _blocknum;
    private byte[] _databuf;

    //---------------------------------

    public DATA(int proto, int blocknum, byte[] databuf) {
        super(proto, DATAop);
        _blocknum = blocknum;
        _databuf = databuf;
    }

    public DATA(int proto, int blocknum, byte[] databuf, int len) {
        super(proto, DATAop);
        _blocknum = blocknum;
        _databuf = databuf;
    }

    public DATA(byte[] buf, int buflen) {
        this(THEPROTO, buf, buflen);
    }

    public DATA(byte[] buf) {
        this(THEPROTO, buf, buf.length);
    }

    // for building a DATA out of incoming buffer;
    // buflen is actual space used, = packet.getLength(),  <= buf.length
    public DATA(int proto, byte[] buf, int buflen) {
        super(proto, DATAop);
        ByteArrayInputStream bais = new ByteArrayInputStream(buf, 0, buflen);
        DataInputStream dis = new DataInputStream(bais);
        try {
            int p = dis.readByte();
            w_assert(p==proto, "Expecting proto " + proto + ", got " + p);
            int o = dis.readByte();
            w_assert(o==DATAop, "Expecting opcode=DATA, got " + o);
            int pad=dis.readShort();
            _blocknum = (dis.readInt());
            _databuf  = new byte[dis.available()];
            dis.read(_databuf);
        } catch (IOException ioe) {
            System.err.println("DATA packet conversion failed");
            return;
        }
    }

    public DATA(int proto) {            // for creating "empty" DATA objects
        super(proto, DATAop);
        _blocknum = 0;
        _databuf = new byte[MAXDATASIZE];
    }

    public DATA() { this(THEPROTO); }

    public int blocknum() {return _blocknum;}
    public byte[] data()  {return _databuf;}
    public byte[] bytes() {return _databuf;}

    public byte[] write() {     // not complete but not needed
        return null;
    }

    public int size() {
        return super.size() + SHORTSIZE + INTSIZE + _databuf.length;
    }
}

//*******************

public static class ERROR extends BASE {

    private short _errcode;

    //---------------------------------
    public ERROR(short proto, short errcode) {
		super(proto, ERRORop);
		_errcode = errcode;
	}

    public short errcode() {return _errcode;}

    public byte[] write() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size());
        DataOutputStream dos = new DataOutputStream(baos);
        try {
            //writeExternal(dos);
            dos.writeByte(super.proto());
            dos.writeByte(super.opcode());
            dos.writeShort(_errcode);
            return baos.toByteArray();
        } catch (IOException ioe) {
            System.err.println("ERROR packet output conversion failed");
            return null;
        }
    }

    public ERROR(byte[] buf) {this(THEPROTO, buf);}

    public ERROR(int proto, byte[] buf) {
        super(proto, ERRORop);
        int opcode = wumppkt.opcode(buf);
        w_assert(opcode == ERRORop, "Expecting opcode=ERROR, got " + opcode);
        w_assert(proto == wumppkt.proto(buf), "Expecting proto="+proto);
        w_assert(buf.length >= BASESIZE + SHORTSIZE, "bad ERROR pkt size of " + buf.length);
        ByteArrayInputStream bais = new ByteArrayInputStream(buf, 0, buf.length);
        DataInputStream dis = new DataInputStream(bais);
        try {
            int p = dis.readByte();
            int o = dis.readByte();
            _errcode = dis.readShort();
        } catch (IOException ioe) {
            System.err.println("ERROR packet conversion failed");
            return;
        }
    };

    public int size() {return super.size() + SHORTSIZE;}
}
}
