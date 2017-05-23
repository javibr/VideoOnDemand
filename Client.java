import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.SwingUtilities;

import uk.co.caprica.vlcj.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.discovery.NativeDiscovery;

//java -cp "vlcj-3.10.1/*:.:jar/*" Client movie.mp4

public class Client{
    

    private final JFrame frame;
    
    private final EmbeddedMediaPlayerComponent mediaPlayerComponent;
    
    private int playing=0;
  //  private final JButton playButton;
    
   // private final JButton pauseButton;

   // private final JButton stopButton;
   
   String serverHost="163.117.144.134";
   int rtspServerPort=45000;
   int rtpRcvPort=5004;
   String videoFileName="/var/home/lab/alum1/02/91/482/Desktop/vlc/movie.mp4";
   static SIPua sipc;
   

    public static void main(final String[] args) {
        new NativeDiscovery().discover();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
            
                new Client(args);
            }
        });
    }
    
    public Client(String[] args) {
        try{ sipc = new SIPua("client","163.117.144.220",46000);}catch (Exception error){
	System.out.println("ERRRRROR");
}
        RTSP rtspConection = new RTSP(serverHost, rtspServerPort, rtpRcvPort, videoFileName);
	String movie = args[0];
	
	try{
	 sipc.initiateSession(movie+"@"+serverHost+":"+"5060");}catch(Exception error){}
       
        frame = new JFrame("Media Player Client");
        
        
        frame.setBounds(100, 100, 600, 400);        
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                mediaPlayerComponent.release();
                System.exit(0);
            }
        });
        
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BorderLayout());
        
        mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        contentPane.add(mediaPlayerComponent, BorderLayout.CENTER);
        
        JPanel controlsPane = new JPanel();

 
 //----------------------
        JButton playButton = new JButton("Play");
        controlsPane.add(playButton);
        JButton pauseButton = new JButton("Pause");
        controlsPane.add(pauseButton);
        JButton stopButton = new JButton("Stop");
        controlsPane.add(stopButton);  
        JButton setupButton = new JButton("Setup");
        controlsPane.add(setupButton);      
        contentPane.add(controlsPane, BorderLayout.SOUTH);
        
        //Handler for PLAY button
        //-----------------------
        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
			rtspConection.send_RTSP_request("PLAY");
            
           // if(playing==0){
             //   playing=1;
                        mediaPlayerComponent.getMediaPlayer().playMedia("rtp://163.117.144.220:5004");
                                      System.out.println("RTSP Client - Datos enviados.(PLAY)");

                //                    }
            }
        });
        
        pauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            rtspConection.send_RTSP_request("PAUSE");
            System.out.println("RTSP Client - Datos enviados.(PAUSE)");

                      //  mediaPlayerComponent.getMediaPlayer().pause();

            }
        });
        
         setupButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            
                      System.out.println("RTSP Client - has pulsado setup, enviando datos");
                      rtspConection.send_RTSP_request("SETUP");
		      System.out.println("RTSP Client - Datos enviados.(SETUP)");

                      //  mediaPlayerComponent.getMediaPlayer().pause();

            }
        });
        
        
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            
            	
		      rtspConection.send_RTSP_request("TEARDOWN");
              System.out.println("RTSP Client - Datos enviados.(STOP)");
             // System.exit(0);
	      try{
	      sipc.terminateSession();} catch (Exception error){}
	     // System.exit(0);
                       // mediaPlayerComponent.getMediaPlayer().stop();
                        //             System.exit(0);


            }
        });
        
        //Makes visible the window
        frame.setContentPane(contentPane);
        frame.setVisible(true);
        
           //mediaPlayerComponent.getMediaPlayer().playMedia("rtp://127.0.0.1:5004");

        
    }
    
}

        
//javac -d classes -cp "vlcj-3.10.1/*:." *.java
//java -cp "../vlcj-3.10.1/*:." Server movie.mp4



