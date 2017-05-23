import java.io.*;
import java.text.ParseException;
import java.util.*;
import java.net.*;
import javax.sdp.*;
import gov.nist.javax.sdp.*;
import gov.nist.javax.sip.*;
import gov.nist.javax.sdp.fields.*;
import gov.nist.javax.sdp.parser.*;
import gov.nist.javax.sip.message.*;
import gov.nist.javax.sip.parser.*;
import gov.nist.javax.sip.header.*;
import gov.nist.javax.sip.address.*;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

	public class SIPua implements SipListener{

		private Address localAddress;
		private SipFactory sipFactory;
		private AddressFactory addressFactory;
		private HeaderFactory headerFactory;
		private MessageFactory messageFactory;
		private SipStack sipStack;
		private SipProvider sipProvider;
		private ListeningPoint udp;
		private SdpFactory sdpFactory;
		private Dialog dialog;
		private Random generator;
		
		// auxiliary variables
		Request invite;
		ServerTransaction inviteServerTransaction;

		private String SIP_DOMAIN = "vod.net";
		
		//TO-DO: set this vatiables to the desired values
		private int RTP_PORT = 45000;
		private int RTSP_PORT = 5060;
		private String RTSP_URI = "rtsp://usr/lab/alum/02/91/482/Desktop/vlc/movie.mp4";
		
		// Variables for the local user/UA
		private String username;
		private String localIPAddress;
		private int localPort;

		// Variables resulting from the session establishment
		private String rtspURI;
		private int rtspPort;
	    private boolean sessionEstablished;

	    
	    
		/********************************************************************************************************/
		/* Constructor method */
		/********************************************************************************************************/
		public SIPua (String username, String localIPAddress, int localPort) throws Exception {
			
			// Initializes local attributes
			this.localIPAddress = localIPAddress;
			this.localPort = localPort;
			this.username = username;
						
			// Initializes the SIP stack
			sdpFactory = SdpFactory.getInstance();
			sipFactory = SipFactory.getInstance();
			sipFactory.setPathName("gov.nist");
			Properties properties = new Properties();
			properties.setProperty("javax.sip.STACK_NAME", "SIPua");
			sipStack = sipFactory.createSipStack(properties);
			udp = sipStack.createListeningPoint(getHost(), getPort(), "udp");
			sipProvider = sipStack.createSipProvider(udp);
			sipProvider.addSipListener(this);
			
			// Creates the Header, Address and Message factories
			headerFactory = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();	

			// Initializes remaining attributes
			SipURI localSipURI = addressFactory.createSipURI( getUsername(), getHost() + ":" + getPort() );
			this.localAddress = addressFactory.createAddress(localSipURI);

			// Initializes the random number generator
			generator = new Random();
			Date date = new Date();
			generator.setSeed(localSipURI.hashCode() + date.getTime());
			
			System.out.println("SIP UA initialized: " + "sip: " + getUsername() + "@"+ getHost() + ":" + getPort());
			sessionEstablished = false;
		}


		/******************************************************************************/
		/* These methods return the username, host and port */
		/******************************************************************************/
		private String getUsername() {
			return username;
		}
		
		private String getHost() throws Exception {
			return localIPAddress;
		}
		
		private int getPort(){
			return localPort;
		}


		/*************************************************************************************/
		/* Generates the first SDP offer */
		/*************************************************************************************/
		public Object createFirstSDPOffer() throws Exception {

			SessionDescription sessionDescription = sdpFactory.createSessionDescription();

			// Sets formats for "m" line
			int[] formats = new int[1];
			formats[0] = 32;

			// Creates an "m" line for RTP media
			MediaDescription mediaDescription = sdpFactory.createMediaDescription("video", RTP_PORT, 1, "RTP/AVP", formats);

			// Sets format attributes
			Vector <Attribute> attributes = new Vector <Attribute> ();
			Attribute a = sdpFactory.createAttribute("rtpmap", "32 MPV");
			attributes.add(a);

			// Sets directivity
			a = sdpFactory.createAttribute("recvonly",null);
			attributes.add(a);

			// Ends with media description
			mediaDescription.setAttributes(attributes);			

			// Sets a "c" line
			Connection connection = sdpFactory.createConnection( getHost() );
			mediaDescription.setConnection(connection);

			// Sets the b parameter as 1Mbps
			BandWidth bandwidth = sdpFactory.createBandwidth(BandWidth.AS, 1000);
			Vector <BandWidth> bandwidths = new Vector <BandWidth> ();
			bandwidths.add(bandwidth);
			mediaDescription.setBandwidths(bandwidths);

			// Creates an "m" line for RTSP 
			String[] formats2 = {"iptv_rtsp"};
			MediaDescription mediaDescription2 = sdpFactory.createMediaDescription("application", 9, 1, "TCP", formats2);

			// Sets TCP attributes
			Vector <Attribute> attributes2 = new Vector <Attribute> ();
			a = sdpFactory.createAttribute("setup", "active");
			attributes2.add(a);
			a = sdpFactory.createAttribute("connection", "new");
			attributes2.add(a);

			// Ends with media description
			mediaDescription2.setAttributes(attributes2);			

			// Sets a "c" line
			mediaDescription2.setConnection(connection);


			Vector <MediaDescription> mediaDescriptions = new Vector <MediaDescription> ();
			mediaDescriptions.add(mediaDescription);
			mediaDescriptions.add(mediaDescription2);
			sessionDescription.setMediaDescriptions(mediaDescriptions);

			return sessionDescription;
		}


		/*************************************************************************************/
		/* Generates the first SDP answer */
		/*************************************************************************************/
		private Object createFirstSDPAnswer(byte[] sdpOffer) throws Exception {

			 SessionDescription sdpAnswer = sdpFactory.createSessionDescription( new String(sdpOffer) );
			 
			 Vector <MediaDescription> mediaDescriptions = sdpAnswer.getMediaDescriptions(true);
			
			 // Updates the "m" line for RTP media
			 MediaDescription mediaDescription = mediaDescriptions.elementAt(0);
			 mediaDescription.getMedia().setMediaPort(RTP_PORT);
			
			 // Gets the information to be used in the user plane
			 // rtpPort = mediaDescription.getMedia().getMediaPort();
			 // rtpAddress = new String( mediaDescription.getConnection().getAddress());

			 // Removes attributes with non conforming values
			 mediaDescription.removeAttribute("recvonly");
			 
			 // Set new attributes
			 Vector <Attribute> attributes = mediaDescription.getAttributes(true);
			 Attribute a = sdpFactory.createAttribute("sendonly", "");
			 attributes.add(a);
			 
			// Sets the b parameter as 1Mbps
			BandWidth bandwidth = sdpFactory.createBandwidth(BandWidth.AS, 1000);
			Vector <BandWidth> bandwidths = new Vector <BandWidth> ();
			bandwidths.add(bandwidth);
			mediaDescription.setBandwidths(bandwidths);

			// Sets a "c" line
			Connection connection = sdpFactory.createConnection( getHost() );
			mediaDescription.setConnection(connection);

			// Updates the "m" line for RTSP
			MediaDescription mediaDescription2 = mediaDescriptions.elementAt(1);
			mediaDescription2.getMedia().setMediaPort(RTSP_PORT);

			// Sets TCP attributes
			mediaDescription2.setAttribute("setup", "passive");
			attributes = mediaDescription2.getAttributes(true);
			a = sdpFactory.createAttribute("fmtp", "iptv_rtsp h-uri=" + RTSP_URI);
			attributes.add(a);

			// Sets a "c" line
			mediaDescription2.setConnection(connection);

			System.out.println("Created first SDP answer: \n" + sdpAnswer.toString() );
			return sdpAnswer;
		}
		

		/******************************************************************************/
		/* Utility to get an unique tag */
		/******************************************************************************/
		private String getTag() throws Exception {
			long number = generator.nextLong();
			return Long.toHexString(number);
		}

		/******************************************************************************/
		/* Processes a SIP request */
		/******************************************************************************/
	    public void processRequest(RequestEvent evt)  {

	    	try {
		    	Request request = evt.getRequest();
		    	System.out.println("Received " + request.getMethod() + " request");
		    	System.out.println(request.toString());
		    	ServerTransaction serverTransaction = evt.getServerTransaction();
		    	
		    	if (serverTransaction == null)
		    		serverTransaction = sipProvider.getNewServerTransaction(request);
		    	
		    	// Processes an INVITE request
		    	if (request.getMethod().equals(Request.INVITE) ){
		    				    		
		    		invite = request;
		    		inviteServerTransaction = serverTransaction;
		    		dialog = serverTransaction.getDialog();
		    		
		    		// Configures the data plane for uplink
		    		SessionDescription sdpOffer = sdpFactory.createSessionDescription( new String(request.getRawContent()) );	

		    		// TO-DO: create a 200 OK response to the INVITE request, using the object "messageFactory"
	    			 Response response = messageFactory.createResponse(Response.OK, request);

	    			// TO-DO: create a Contact header using the object "headerFactory"
					// ...
				ContactHeader contactHeader = headerFactory.createContactHeader(localAddress);
	    			response.addHeader(contactHeader);
	    		 
					//Adds an SDP answer
		    		ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
		    		response.setContent( createFirstSDPAnswer( invite.getRawContent() ), contentTypeHeader);
	
	    			// Sends the response	    		
	    			inviteServerTransaction.sendResponse(response);
				}
		    	
		    	// Processes an ACK request
		    	else if (request.getMethod().equals(Request.ACK)) {
		    		sessionEstablished = true;
		    	}
		
		    	// Processes a BYE request
		    	else if (request.getMethod().equals(Request.BYE)) {

		    		// Creates a 200 OK response
	    			Response response = messageFactory.createResponse(Response.OK, request);
					serverTransaction.sendResponse(response);
		    	}
		
		    	
	    	}catch (Exception e) {
	    		e.printStackTrace();
	    	}
	    }

	    
	    /*****************************************************************************/
	    /* Processes a SIP response */
	    /*****************************************************************************/
	    public void processResponse(ResponseEvent evt) {
	    	try {
		    	Response response = evt.getResponse();
		    	dialog = evt.getDialog();
		    	
		    	System.out.println("Received response:" + "\n" + response.toString());
		    	
		    	// Processes a 200 OK response
		    	if (response.getStatusCode() == Response.OK) {
		    		
		    		CSeqHeader cseqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
		    	
		    		// Processes an OK response to an INVITE request
		    		if (cseqHeader.getMethod().equals(Request.INVITE)) {	    			
		    			
                        System.out.println(" OK response received for INVITE request. Sending ACK request");
                        
						//TO-DO create and send an ACK, using the object "dialog"
						Request ack = dialog.createRequest(Request.ACK);
						dialog.sendAck(ack);

		    			
		    			System.out.println("Session established");							
						sessionEstablished = true;
						processSDP(response.getRawContent());
		    		}
		    	}
		    	
	    	}
	    	catch (Exception e) {
	    		e.printStackTrace();
	    	}
	    }
	    
		
	    /*****************************************************************************/
	    /* Processes the SDP answer to obtain the RTSP URI of the requested video */
	    /*****************************************************************************/
		private void processSDP(byte[] sdpAnswer) throws Exception{

			SessionDescription sdpPayload = sdpFactory.createSessionDescription( new String(sdpAnswer) );
			Vector <MediaDescription> mediaDescriptions = sdpPayload.getMediaDescriptions(true);
			MediaDescription mediaDescription = mediaDescriptions.elementAt(1);
			String aux = mediaDescription.getAttribute("fmtp");
			rtspURI = aux.substring(aux.indexOf("=")+1);
			rtspPort = mediaDescription.getMedia().getMediaPort();
		}    
	
	
	    /*****************************************************************************/
	    /* Returns the RTSP URI of the video, in case that the session has been */
	 	/* establihsed, null otherwise */
	    /*****************************************************************************/
		public String getRtspURI() {
			if (sessionEstablished) return rtspURI;
			else return null;
		}
	
	
	    /*****************************************************************************/
	    /* Returns the port of the RTSP server, in case that the session has been */
	 	/* establihsed, -1 otherwise */
	    /*****************************************************************************/
		public int getRtspPort() {
			if (sessionEstablished) return rtspPort;
			else return -1;
		}
	
	    /*****************************************************************************/
	    /* It is not neccessary to implement this */
	    /*****************************************************************************/
	    public void processTimeout(TimeoutEvent evt) {
	    }    
	    public void processIOException(IOExceptionEvent evt) {    	
	    }
	    public void processTransactionTerminated(TransactionTerminatedEvent evt) {    	
	    }
	    public void processDialogTerminated(DialogTerminatedEvent evt) {    	
	    }

	    
	    /*****************************************************************************/
	    /* Creates an initial INVITE request */
	    /*****************************************************************************/
		private Request createINVITE(String to) throws Exception {

				// Creates the From header
			  	SipURI from = addressFactory.createSipURI(getUsername(), SIP_DOMAIN);
				Address fromNameAddress = addressFactory.createAddress(from);
				fromNameAddress.setDisplayName(getUsername());	
				FromHeader fromHeader = headerFactory.createFromHeader(fromNameAddress, getTag());
				long random=generator.nextLong();
				// Creates the To header
				String username = to.substring(0, to.indexOf("@"));
				String address = to.substring(to.indexOf("@")+1);
				SipURI toAddress = addressFactory.createSipURI(username, address);			
				Address toNameAddress = addressFactory.createAddress(toAddress);
				toNameAddress.setDisplayName(username);
				ToHeader toHeader = headerFactory.createToHeader(toNameAddress, null);
				
				// TO-DO: create the Request-URI using the object "addressFactory"
				 SipURI requestURI = addressFactory.createSipURI(username,address);
				// ...

				// TO-DO: create the Via Header using the method "headerFactory.createViaHeader"
				ViaHeader viaHeader = headerFactory.createViaHeader(getHost(), getPort(), "UDP", getTag());
				ArrayList viaHeaders = new ArrayList();
				viaHeaders.add(viaHeader);

				// TO-DO: create the Call-ID header using the object "sipProvider"
				CallIdHeader callIdHeader = sipProvider.getNewCallId();

				// TO-DO: create CSeq header using the method "headerFactory.createCSeqHeader"
				CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(123456,"INVITE");

				// TO-DO: create Max-Forwards header using the object "headerFactory"
				MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
				
				// Creates the request
				Request request =  messageFactory.createRequest(requestURI, Request.INVITE, callIdHeader, cSeqHeader,	fromHeader, toHeader, viaHeaders, maxForwards);
				
				// TO-DO: create the Contact header, similarly to the To header, using the object "headerFactory"
				// ...
				 ContactHeader contactHeader = headerFactory.createContactHeader(localAddress);
				request.addHeader(contactHeader);

				// Creates Content-type header
				ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
				request.addHeader(contentTypeHeader);
				request.setContent( createFirstSDPOffer(), contentTypeHeader);		
								
				return request;
		}
		


		/*****************************************************************************/
		/* Initiates the session, by generating and sending an initial INVITE request */
		/*****************************************************************************/
		public void initiateSession(String to) throws Exception {
					
			System.out.print("Creating INVITE request... ");
			Request invite = createINVITE(to);
			System.out.println("done");
			
			System.out.println("Sending the INVITE request... \n" + invite.toString());
			ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(invite);
			clientTransaction.sendRequest();
		}
	
	
	    /*****************************************************************************/
	    /* Terminates the session */
	    /*****************************************************************************/
	   	public void terminateSession() throws Exception{

			// Creates BYE request
	    	Request bye = dialog.createRequest(Request.BYE);	    			
			System.out.print("Sending BYE request...");

			ClientTransaction clientTransaction = sipProvider.getNewClientTransaction(bye);
			dialog.sendRequest(clientTransaction);
			System.out.println("done");	    		
	    }


	
		/*****************************************************************************/
		/* Releases the SIP stack */
		/*****************************************************************************/
		public void finalize() throws Exception {
			System.out.println("Deleting listening point...");
			sipStack.deleteListeningPoint(udp);
		}
		
		
	}

	
