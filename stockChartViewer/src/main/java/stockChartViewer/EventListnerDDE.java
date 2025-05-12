package stockChartViewer;

import java.util.concurrent.CountDownLatch;

import com.pretty_tools.dde.DDEException;
import com.pretty_tools.dde.DDEMLException;
import com.pretty_tools.dde.client.DDEClientConversation;
import com.pretty_tools.dde.client.DDEClientEventListener;

public class EventListnerDDE {

	static final String SERVICE = "FDF";
	static final String TOPIC = "Q";
	
	// event to wait disconnection
	final CountDownLatch eventDisconnect = new CountDownLatch(1);

	// DDE client
	DDEClientConversation conversation;
	// We can use UNICODE format if server prefers it
	//conversation.setTextFormat(ClipboardFormat.CF_UNICODETEXT);
	
	public EventListnerDDE() {
		super();
		this.conversation = new DDEClientConversation();
	}
	
	public void initEventListner() {
		try {

			// event to wait disconnection
			final CountDownLatch eventDisconnect = new CountDownLatch(1);

			// DDE client
			final DDEClientConversation conversation = new DDEClientConversation();
			// We can use UNICODE format if server prefers it
			//conversation.setTextFormat(ClipboardFormat.CF_UNICODETEXT);

			conversation.setEventListener(new DDEClientEventListener(){
				public void onDisconnect(){
					System.out.println("onDisconnect()");
					eventDisconnect.countDown();
				}

				public void onItemChanged(String topic, String item, String data){
					//                	try {
					//						persistData(fileName, data);
					//					} catch (IOException e1) {
					//						e1.printStackTrace();
					//					}
					String key = topic+"-"+item;
					

					System.out.println("onItemChanged(" + topic + "," + item + "," + data + ")");
					try{
						if ("stop".equalsIgnoreCase(data)) {
							conversation.stopAdvice(item);
							System.out.println("server stop signal (" + topic + "," + item + "," + data + ")");
						}
					}
					catch (DDEException e){
						System.out.println("Exception: " + e);
						e.printStackTrace();
					}
				}
			});

			System.out.println("Connecting...");
			conversation.connect(SERVICE, TOPIC);
			//for(String it : configItemsListnerList) {conversation.startAdvice(it);}
			conversation.startAdvice("MESM5.CME;last");
			//            conversation.startAdvice(item);
			//            conversation.startAdvice(item2);

			System.out.println("Waiting event...");
			eventDisconnect.await();
			System.out.println("Disconnecting...");
			conversation.disconnect();
			System.out.println("Exit from thread");
		}
		catch (DDEMLException e){
			System.out.println("DDEMLException: 0x" + Integer.toHexString(e.getErrorCode()) + " " + e.getMessage());
		}
		catch (DDEException e){
			System.out.println("DDEClientException: " + e.getMessage());
		}
		catch (Exception e){
			e.printStackTrace();
			//System.out.println("Exception: " + e);
		} finally {

		}
	}
	
}
