package stockChartViewer;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.pretty_tools.dde.DDEException;
import com.pretty_tools.dde.DDEMLException;
import com.pretty_tools.dde.client.DDEClientConversation;
import com.pretty_tools.dde.client.DDEClientEventListener;

import axi.apis.GoogleSheetApiManager;
import axi.apis.GoogleSheetApiManager.ItemModel;

public class EventListnerDDE {

	static final String SERVICE = "FDF";
	static final String TOPIC = "Q";
	
	// event to wait disconnection
	final CountDownLatch eventDisconnect = new CountDownLatch(1);

	// DDE client
	DDEClientConversation conversation;
	// We can use UNICODE format if server prefers it
	//conversation.setTextFormat(ClipboardFormat.CF_UNICODETEXT);
	
	String spreadsheetId = "1rWwN4dYpuobNZCmi8rT514JGHdmvZsZNi38Ws2ePCI0";
	GoogleSheetApiManager gapi;
	
	public EventListnerDDE() {
		super();
		
		try {
			gapi = new GoogleSheetApiManager(spreadsheetId);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GeneralSecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//		ItemModel im1 = new ItemModel("MESM5.CME;last"),
//		          im2 = new ItemModel("MNQM5.CME;last");
//		itemsMap.put(im1.getItem(), im1);
//		itemsMap.put(im2.getItem(), im2);
		
		this.conversation = new DDEClientConversation();
	}
	
	public void initEventListner(String[] items) {
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
					
					
//					String range = null;
//					if(item.equals("MESM5.CME;last")) range = "TEST-SPREAD-TRADING!H3:H3";
//					if(item.equals("MNQM5.CME;last")) range = "TEST-SPREAD-TRADING!H4:H4"; 
//					GoogleSheetApiManager.sendData(range, data);
					
					Map<String, ItemModel> itemsMap = gapi.getItemsMap();
					ItemModel im = gapi.getItemsMap().get(item);
					if(im!=null) {
						im.setData(data);
					} else {
						im = gapi.new ItemModel(item, data);
						itemsMap.put(im.getItem(), im);
					}

					
					
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
			
			
			//conversation.startAdvice("MESM5.CME;last");
			for(String i : items) conversation.startAdvice(i);
			
			
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
