/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*;     //pld
import java.net.*;      //pld
import java.io.*;
//import wumppkt;         // be sure wumppkt.java is in your current directory
//import java.io.Externalizable;

// As is, this packet should receive data[1] and time out.
// If you send the ACK to the correct port, you should receive data[2]
// If you update expected_block, you should receive the entire file, for "vanilla"
// If you write the sanity checks, you should receive the entire file in all cases

public class windows {

    //============================================================
    //============================================================

    static public void main(String args[]) {
        int srcport;
        int destport = wumppkt.SERVERPORT;
	      //int destport = wumppkt.SAMEPORT;		// 4716; server responds from same port
        String filename = "reorder";
        String desthost = "ulam.cs.luc.edu";
        //String desthost = "localhost";
        int winsize = 5;
      	int latchport = 0;
      	short THEPROTO = wumppkt.BUMPPROTO;
      	wumppkt.setproto(THEPROTO);

        if (args.length > 0) filename = args[0];
        if (args.length > 1) winsize = Integer.parseInt(args[1]);
        if (args.length > 2) desthost = args[2];

        DatagramSocket s;
        try {
            s = new DatagramSocket();
        }
        catch (SocketException se) {
            System.err.println("no socket available");
            return;
        }

        try {
            s.setSoTimeout(wumppkt.INITTIMEOUT);       // time in milliseconds
        } catch (SocketException se) {
            System.err.println("socket exception: timeout not set!");
        }

       if (args.length > 3) {
            System.err.println("usage: wclient filename  [winsize [hostname]]");
            //exit(1);
        }

		    // DNS lookup
        InetAddress dest;
        System.err.print("Looking up address of " + desthost + "...");
        try {
            dest = InetAddress.getByName(desthost);
        }
        catch (UnknownHostException uhe) {
            System.err.println("unknown host: " + desthost);
            return;
        }
        System.err.println(" got it!");

		    // build REQ & send it
        wumppkt.REQ req = new wumppkt.REQ(winsize, filename); // ctor for REQ

        System.err.println("req size = " + req.size() + ", filename=" + req.filename());

        DatagramPacket reqDG
            = new DatagramPacket(req.write(), req.size(), dest, destport);
        try {s.send(reqDG);}
        catch (IOException ioe) {
            System.err.println("send() failed");
            return;
        }

        //============================================================

        // now receive the response
        DatagramPacket replyDG            // we don't set the address here!
            = new DatagramPacket(new byte[wumppkt.MAXSIZE] , wumppkt.MAXSIZE);
        DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
	      DatagramPacket lastSent = reqDG;	// pld: this is what was last sent
        ackDG.setAddress(dest);
	      ackDG.setPort(destport);		// this is wrong for wumppkt.SERVERPORT version

        int expected_block = 1;
        long starttime = System.currentTimeMillis();
        long sendtime = starttime;

        wumppkt.DATA  data  = null;
        wumppkt.ERROR error = null;
        wumppkt.ACK   ack   = new wumppkt.ACK(0);

        int proto;        // for proto of incoming packets
        int opcode;
        int length;
	      int blocknum;

        //====== MAIN LOOP ================================================
        /*
        Upon arrival of Data[M]

        W: winsize
        LA: last_ACKed

        if M≤LA or M>LA+W, ignore the packet
        if M>LA+1, put the packet into EarlyArrivals.
        if M==LA+1:
        deliver the packet (that is, Data[LA+1]) to the application
        LA = LA+1 (slide window forward by 1)
        while (Data[LA+1] is in EarlyArrivals) {
        output Data[LA+1]
        LA = LA+1
        }
        send ACK[LA]
        */

        wumppkt.DATA[] earlyArrivals = new wumppkt.DATA[winsize];
        for(int i=0; i<earlyArrivals.length; i++)
        {
          earlyArrivals[i] = null;
        }

        while (true) {
            //Checks for timeout, retransmits previous packet if necessary
            if(System.currentTimeMillis() > (sendtime + 2000))
            {
                try
                {
                    s.send(lastSent);
                }
                catch(IOException ioe)
                {
                    System.err.println("send() failed");
                    return;
                }
                sendtime = System.currentTimeMillis();
            }

            // get packet
            try {
                s.receive(replyDG);
            }
            catch (SocketTimeoutException ste)
            {
          			System.err.println("hard timeout");
        				continue;
            }
            catch (IOException ioe) {
                System.err.println("receive() failed");
                return;
            }

            byte[] replybuf = replyDG.getData(); //Used for BUMP
            //replybuf = replyDG.getData(); //Used for HUMP
            proto   = wumppkt.proto(replybuf);
            opcode  = wumppkt.opcode(replybuf);
            length  = replyDG.getLength();
            srcport = replyDG.getPort();

            /* The new packet might not actually be a DATA packet.
             * But we can still build one and see, provided:
             *   1. proto =   THEPROTO
             *   2. opcode =  wumppkt.DATAop
             *   3. length >= wumppkt.DHEADERSIZE
             */

      	    data = null; error = null;
      	    blocknum = 0;
      	    if (  proto == THEPROTO && opcode == wumppkt.DATAop && length >= wumppkt.DHEADERSIZE)
            {
                data = new wumppkt.DATA(replybuf, length);
                blocknum = data.blocknum();
      	    }
            else if ( proto == THEPROTO && opcode == wumppkt.ERRORop && length >= wumppkt.EHEADERSIZE)
            {
      	        error = new wumppkt.ERROR(replybuf);
            }

      	    printInfo(replyDG, data, starttime);

      	    // now check the packet for appropriateness
      	    // if it passes all the checks:
                  //write data, increment expected_block
      	    // exit if data size is < 512

      	    if (error != null) {
      	        System.err.println("Error packet rec'd; code " + error.errcode());
                if(error.errcode() == 4)
                {
                    System.err.println("Nonexistent file.");
                    break;
                }
                else if(error.errcode() == 5)
                {
                    System.err.println("Invalid permissions.");
                    break;
                }
                else
                {
                    continue;
                }
      	    }
            //Sliding windows
            int lastWritten = expected_block-1;
            if(blocknum < lastWritten+1 || blocknum > (lastWritten + winsize))
            {
              continue;
            }
            else if(blocknum > lastWritten+1)
            {
              earlyArrivals[blocknum%winsize] = data;
            }
            else if(blocknum == lastWritten+1)
            {
              earlyArrivals[(blocknum)%winsize] = data;
              while(earlyArrivals[(lastWritten+1)%winsize] != null)
              {
                    data=earlyArrivals[(lastWritten+1)%winsize];
                    earlyArrivals[(lastWritten+1)%winsize] = null;
                    // The following is for you to do:
                    // check timeouts, host/port, size, opcode, and block number
                    // latch on to port, if block == 1

                    if(!replyDG.getAddress().getHostAddress().equals(dest.getHostAddress())) //JC, checks host
                    {
                      System.err.println("ERROR: Invalid host IP");
                      continue;
                    }
                    if(expected_block != 1 && replyDG.getPort() != latchport) //JC, checks port
                    {
                      System.err.println("ERROR: Invalid host port");

                      //Must send error packet to wherever the bad packet came from
                      error = new wumppkt.ERROR(THEPROTO, (short)wumppkt.EBADPORT);
                      DatagramPacket errorPacket = new DatagramPacket(error.write(), error.size(), dest, replyDG.getPort());
                      try
                      {
                        s.send(errorPacket);
                      }
                      catch(IOException ioe)
                      {
                        System.err.println("send() failed");
                      }

                      continue;
                    }
                    if(length > wumppkt.MAXSIZE || length < wumppkt.DHEADERSIZE) //JC, checks packet size
                    {
                      System.err.println("ERROR: Invalid packet size");
                      continue;
                    }
                    if(proto != THEPROTO) //JC, checks proto
                    {
                      System.err.println("ERROR: Invalid protocol");
                      continue;
                    }
                    if(opcode != wumppkt.DATAop) //JC, checks opcode
                    {
                      System.err.println("ERROR: Invalid opcode");
                      continue;
                    }
                    if(blocknum != expected_block) //JC, checks block number

                    {
                      System.err.println("ERROR: Invalid block number");
                      continue;
                    }
                    if (data == null)
                    {
                      continue;		// typical error check, but you should
                    }
                    if(blocknum == 1) //JC, latch onto port if blocknum == 1
                    {
                      latchport = replyDG.getPort();
                    }

              	    // write data
              	    System.out.write(data.bytes(), 0, data.size() - wumppkt.DHEADERSIZE);
                    lastWritten = lastWritten++;
              }

              // send ack
              expected_block = lastWritten+1;
              ack = new wumppkt.ACK(expected_block);
              expected_block++; //JC
              ackDG.setData(ack.write());
              ackDG.setLength(ack.size());
              ackDG.setPort(srcport); //JC
              lastSent = ackDG;
              try {s.send(ackDG);}
              catch (IOException ioe) {
                  System.err.println("send() failed");
                  return;
              }

              sendtime = System.currentTimeMillis();

              //JC, transition to dally then quit
              if(data.size() < 512)
              {
                //JC, dally
                long elapsedTime=0;
                while(elapsedTime < wumppkt.INITTIMEOUT*2) //For 2 timeout periods
                {
                  elapsedTime=System.currentTimeMillis()-sendtime;
                  try
                  {
                      s.receive(replyDG); //Continually try to receive
                  }
                  catch (SocketTimeoutException ste){
                    continue;
                  }
                  catch (IOException ioe){
                    continue;
                  }

                  //If something received in the two seconds
                  if(data.blocknum() == expected_block-1 && data.size()<512)
                  {
                      try
                      {
                        s.send(ackDG); //Resend final ACK
                        sendtime = System.currentTimeMillis(); //Reset clock
                      }
                      catch (IOException ioe) {
                          System.err.println("send() failed");
                          return;
                      }
                  }
                }
                return; //Else quit
              }

            }
        } // while

    }

    // print packet length, protocol, opcode, source address/port, time, blocknum
    static public void printInfo(DatagramPacket pkt, wumppkt.DATA data, long starttime) {
        byte[] replybuf = pkt.getData();
        int proto = wumppkt.proto(replybuf);
        int opcode = wumppkt.opcode(replybuf);
        int length = replybuf.length;
	// the following seven items we can print always
        System.err.print("rec'd packet: len=" + length);
        System.err.print("; proto=" + proto);
        System.err.print("; opcode=" + opcode);
        System.err.print("; src=(" + pkt.getAddress().getHostAddress() + "/" + pkt.getPort()+ ")");
        System.err.print("; time=" + (System.currentTimeMillis()-starttime));
        System.err.println();
        if (data==null)
            System.err.println("         packet does not seem to be a data packet");
        else
            System.err.println("         DATA packet blocknum = " + data.blocknum());
    }

    // extracts blocknum from raw packet
    // blocknum is laid out in big-endian order in b[4]..b[7]
    static public int getblock(byte[] buf) {
        //if (b.length < 8) throw new IOException("buffer too short");
        return  (((buf[4] & 0xff) << 24) |
            		 ((buf[5] & 0xff) << 16) |
            		 ((buf[6] & 0xff) <<  8) |
            		 ((buf[7] & 0xff)      ) );
    }


}
