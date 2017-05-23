/* ------------------
 Server
 usage: java Server [RTSP listening port]
 ---------------------- */


import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;

import uk.co.caprica.vlcj.binding.LibVlc;
import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.runtime.RuntimeUtil;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;

//java -cp "vlcj-3.10.1/*:.:jar/*" Server 45000

public class Server extends JFrame{
    
    //RTP variables:
    //----------------
    InetAddress ClientIPAddr; //Client IP address
    int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)
    
    //GUI:
    //----------------
    private final JFrame frame;
    
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;
    
    private int inicio=0;
    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    //rtsp message types
    final static int SETUP = 3;
    final static int PLAY = 4;
    final static int PAUSE = 5;
    final static int TEARDOWN = 6;
    
    static int state; //RTSP Server state == INIT or READY or PLAY
    Socket RTSPsocket; //socket used to send/receive RTSP messages
    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName="movie.mp4"; //video file requested from the client
    static int RTSP_ID = 123456; //ID of the RTSP session
    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
    int first_time=0; /****///0 if the video is not load yet
    
    final static String CRLF = "\r\n";
    
    
    
    //------------------------------------
    //main
    //------------------------------------
    
    public static void main(final String[] args) {
        new NativeDiscovery().discover();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try{
                new Server(args);
                }catch (Exception e){}
            }
        });
    }
    
    
    //--------------------------------
    //Constructor
    //--------------------------------
    public Server(String[] args) throws Exception{
    

	        SIPua sips = new SIPua("server","163.117.144.134",45000);
        int RTSPport = Integer.parseInt(args[0]);
        
        //Initiate TCP connection with the client for the RTSP session
        ServerSocket listenSocket = new ServerSocket(45000);
        RTSPsocket = listenSocket.accept();
        
        //Get Client IP address
        ClientIPAddr = RTSPsocket.getInetAddress();
        System.out.println("AAAAAAAAAAAAAA");

        
        listenSocket.close();
        
        //Initiate RTSPstate
        state = INIT;
        
        //GUI initialization
        frame = new JFrame("Media Player Server");
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();

        frame.setContentPane(mediaPlayerComponent);
        frame.setLocation(100, 100);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //Set input and output stream filters:

        RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()) );
        RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()) );

        //Wait for the SETUP message from the client
        int request_type;
        boolean done = false;
        System.out.println(args[0]);
        while(!done)
        {
            request_type = parse_RTSP_request(); //blocking
            
            if (request_type == SETUP)
            {
                done = true;
                 //update RTSP state
                state = READY;
                System.out.println("New RTSP state: READY");

                //Send response
                send_RTSP_response();
             
                
               // show GUI:
                frame.setVisible(true);

            }
        }
        
        //loop to handle RTSP requests
        while(true)
        {
            //parse the request
            request_type = parse_RTSP_request(); //blocking
            
            if ((request_type == PLAY) && (state == READY))
            {
                //send back response
                send_RTSP_response();
                
                //start media player
                if(inicio==0)
                mediaPlayerComponent.getMediaPlayer().playMedia(VideoFileName, ":sout=#rtp{dst=163.117.144.220,port=5004,mux=ts}", ":no-sout-rtp-sap", ":no-sout-standard-sap", ":sout-all", ":sout-keep");                
                
                mediaPlayerComponent.getMediaPlayer().play();

                //update state
                
                state = PLAYING;
                inicio=1;
                System.out.println("New RTSP state:... " +state);
                                   
            }  //complete the rest of feasible states
            else if ((request_type == PAUSE) && (state == PLAYING)){
            
            send_RTSP_response();
            mediaPlayerComponent.getMediaPlayer().pause();
            
            //update state
                 state = READY;
                 System.out.println("New RTSP state:"+ state);
            
            }
            else if (request_type==TEARDOWN){
            
            send_RTSP_response();
            
            state=INIT;
              System.out.println("New RTSP state: "+ state);
              //close sockets
            mediaPlayerComponent.getMediaPlayer().stop();
            frame.setVisible(false);

           // frame.setVisible(false);
            //System.exit(0);
            
            }
            else if((request_type==SETUP)&&(state==INIT)){
            
            send_RTSP_response();
            state=READY;
            System.out.println("New RTSP state: "+ state);
 
            frame.setVisible(true);
            
            
            }
        }
    }
    
    
    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parse_RTSP_request()
    {
        int request_type = -1;
        try{
            //parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            //System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);
            
            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();
            
            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;
            
            if (request_type == SETUP)
            {
                //extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }
            
            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());
            
            //get LastLine
            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);
            
            if (request_type == SETUP)
            {
                //extract RTP_dest_port from LastLine
                tokens = new StringTokenizer(LastLine);
                for (int i=0; i<3; i++)
                    tokens.nextToken(); //skip unused stuff
                RTP_dest_port = Integer.parseInt(tokens.nextToken());
        
            }
            //else LastLine will be the SessionId line ... do not check for now.
        }
        catch(Exception ex)
        {
            System.out.println("Exception caught 2: "+ex);
            System.exit(0);
        }
        return(request_type);
    }
    
    
    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    private void send_RTSP_response()
    {
        try{
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
            RTSPBufferedWriter.flush();
            //System.out.println("RTSP Server - Sent response to Client.");
        }
        catch(Exception ex)
        {
            System.out.println("Exception caught 3: "+ex);
            System.exit(0);
        }
    }
}
