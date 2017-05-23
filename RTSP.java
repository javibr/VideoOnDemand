import java.io.*;
import java.net.*;
import java.util.*;

//javac  -cp "vlcj-3.10.1/*:.:jar/*" *java

public class RTSP{
	
	
    private String ServerHost;
    
    private int RTP_RCV_PORT;
	//RTSP variables
	//----------------
	//rtsp states 
	final static int INIT = 0;
	final static int READY = 1;
	final static int PLAYING = 2;
	static int state; //RTSP state == INIT or READY or PLAYING
	Socket RTSPsocket; //socket used to send/receive RTSP messages
    private int RTSP_server_port;
    
	//input and output stream filters
	static BufferedReader RTSPBufferedReader;
	static BufferedWriter RTSPBufferedWriter;
    
	static String VideoFileName; //video file to request to the server
	int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
	int RTSPid = 0; //ID of the RTSP session (given by the RTSP Server)
	
	final static String CRLF = "\r\n";
	
	
	//--------------------------
	//Constructor
	//--------------------------
	public RTSP(String ServerHost, int RTSP_server_port, int RTP_RCV_PORT, String VideoFileName) {
		
        
            this.ServerHost=ServerHost;
            this.RTSP_server_port=RTSP_server_port;
            this.RTP_RCV_PORT=RTP_RCV_PORT;
            this.VideoFileName=VideoFileName;
        
            //first state
            state=INIT;
        
        try{
            //Establish a TCP connection with the server to exchange RTSP messages
            //------------------
            InetAddress ServerIPAddr = InetAddress.getByName(ServerHost);
            RTSPsocket = new Socket(ServerIPAddr, RTSP_server_port);
   
            //Set input and output stream filters:
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );
        }catch(Exception e){
        
            System.out.println(e+ " "+ServerHost+RTSP_server_port);
        }
        
    }
    

	//------------------------------------
	//Parse Server Response
	//------------------------------------
	private int parse_server_response() 
	{
		int reply_code = 0;
		
		try{
			//parse status line and extract the reply_code:
			String StatusLine = RTSPBufferedReader.readLine();
			//System.out.println("RTSP Client - Received from Server:");
			System.out.println(StatusLine);
			
			StringTokenizer tokens = new StringTokenizer(StatusLine);
			tokens.nextToken(); //skip over the RTSP version
			reply_code = Integer.parseInt(tokens.nextToken());
			
			//if reply code is OK get and print the 2 other lines
			if (reply_code == 200)
			{
				String SeqNumLine = RTSPBufferedReader.readLine();
				System.out.println(SeqNumLine);
				
				String SessionLine = RTSPBufferedReader.readLine();
				System.out.println(SessionLine);
				
				//if state == INIT gets the Session Id from the SessionLine
				tokens = new StringTokenizer(SessionLine);
				tokens.nextToken(); //skip over the Session:
				RTSPid = Integer.parseInt(tokens.nextToken());
			}
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught RTSP: "+ex);
			System.exit(0);
		}
		
		return(reply_code);
	}


	//------------------------------------
	//Send RTSP Request
	//------------------------------------
	
	//.............
	//TO COMPLETE
	//.............
	
	public void send_RTSP_request(String request_type)
	{
		try{
            //Check request_type and state variables to see if the RTSP message can be sent
            
            RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);
            //write the CSeq line: 

            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            
            if(((request_type).compareTo("SETUP") == 0) && (state == INIT)){
            
            RTSPBufferedWriter.write("Transport: RTP/UDP; client_port= " + RTP_RCV_PORT + CRLF);
            
            }
            
            else  {
                RTSPBufferedWriter.write("Session"+RTSPid + CRLF);

            }
            
            
            
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Client - Sent request to Server.");

            //Increment CSeq
	     RTSPSeqNb++;			
            //Wait for the response and, in case of success, update the state variable
            //...
            int response=parse_server_response();
            
	    if(response == 200){
		  if((state==INIT)&&(request_type.compareTo("SETUP")==0)){
		  state=READY;}
		  if((state==READY)&&(request_type.compareTo("PLAY")==0)){
		  state=PLAYING;}
		  if((state==READY)&&(request_type.compareTo("TEARDOWN")==0)){
		  state=INIT;}
		  if((state==PLAYING)&&(request_type.compareTo("TEARDOWN")==0)){
		  state=INIT;}
		  if((state==PLAYING)&&(request_type.compareTo("PAUSE")==0)){
		  state=READY;}
	    
	    }
	      
  
		}
		catch(Exception ex)
		{
			System.out.println("Exception caught: "+ex);
			System.exit(0);
		}
	}


}//end of Class RTSP
