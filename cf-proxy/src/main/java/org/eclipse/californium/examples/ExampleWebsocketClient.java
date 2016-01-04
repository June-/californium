package org.eclipse.californium.examples;

import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Random;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.serialization.DataParser;
import org.eclipse.californium.core.network.serialization.DataSerializer;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class ExampleWebsocketClient extends JFrame implements ActionListener {
	// 日志
	// private final static Logger LOGGER =
	// Logger.getLogger(ProxyWebsocketServer.class.getCanonicalName());

	private final JTextField uriField; /* ws://localhost:8887 */
	private final JTextField coapReqField; /* coap://localhost:5683/target */
	private final JButton connect; // 连接
	private final JButton send; // 发送coap请求
	private final JTextArea ta; // 显示日志和消息
	private WebSocketClient wsc;
	private Request req;

	/*
	 * just 画界面
	 */
	public ExampleWebsocketClient(String defaultProxyLocation) {
		super("CoAP Endpoint (WebSocket Client)");

		Container c = getContentPane();
		GridLayout layout = new GridLayout();
		layout.setColumns(1);
		layout.setRows(5);
		c.setLayout(layout);

		uriField = new JTextField();
		uriField.setName("WebSocket代理");
		uriField.setText(defaultProxyLocation);
		c.add(uriField);

		connect = new JButton("连接");
		connect.addActionListener(this);
		c.add(connect);

		coapReqField = new JTextField();
		coapReqField.setName("目标资源");
		coapReqField.setText("coap://localhost:5683/target");
		c.add(coapReqField);

		send = new JButton("发送请求");
		send.addActionListener(this);
		send.setEnabled(false);
		c.add(send);

		ta = new JTextArea();
		JScrollPane scroll = new JScrollPane(ta);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		c.add(scroll);

		java.awt.Dimension d = new java.awt.Dimension(350, 500);
		setPreferredSize(d);
		setSize(d);

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (wsc != null) {
					wsc.close();
				}
				dispose();
			}
		});

		setLocationRelativeTo(null);
		setVisible(true);

	}

	/*
	 * 响应事件(点击连接按钮)
	 */
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == connect) {
			try {
				//
				wsc = new WebSocketClient(new URI(uriField.getText())) {
					@Override
					public void onMessage(String message) {
						ta.append(">> (收到代理的文本消息)\n" + message + "\n");
						ta.setCaretPosition(ta.getDocument().getLength());
					}

					@Override
					public void onMessage(ByteBuffer bytes) {
						ta.append(">> (收到代理的二进制消息)\n" + bytes + "\n");
						DataParser dp = new DataParser(bytes.array());
						Response response = dp.parseResponse();
						ta.append(response.toString());
					}

					@Override
					public void onOpen(ServerHandshake handshake) {
						ta.append("状态：与代理的 WebSocket 连接已建立 ：）\n");
						send.setEnabled(true);
					}

					@Override
					public void onClose(int code, String reason, boolean remote) {
					}

					@Override
					public void onError(Exception ex) {
						ex.printStackTrace();
					}

				};

				wsc.connect();

				// LOGGER.info("状态：正在建立连接...\n");
				ta.append("状态：正在建立连接...\n"); // OK

			} catch (URISyntaxException ex) {
				ta.append(uriField.getText() + "非有效的 WebSocket URI");
			}
		} else if (e.getSource() == send) {
			// 这个应写在连接按钮点下以后
			// LOGGER.info("状态：正在发送 coap 请求\n");
			ta.append("\n状态：正在发送 coap 请求\n");

			wsc.send(getBinaryCoapRequest());
		}

	}

	private byte[] getBinaryCoapRequest() {
		Request req = new Request(Code.GET, org.eclipse.californium.core.coap.CoAP.Type.CON);
		// req.setURI("coap://localhost:5683/target");
		req.setURI(coapReqField.getText());
		int counter = new Random().nextInt();
		int token = counter++;
		req.setToken(new byte[] { (byte) (token >>> 24), (byte) (token >>> 16), (byte) (token >>> 8), (byte) token });
		counter = new Random().nextInt();
		int mid = Math.abs(counter++ % (1 << 16));
		req.setMID(mid);

		DataSerializer ds = new DataSerializer();
		return ds.serializeRequest(req);
	}

	public static void main(String[] args) {
		WebSocketImpl.DEBUG = true;
		String location = "ws://localhost:8887";
		if (args.length != 0) {
			location = args[0];
			System.out.println("Default server url specified: \'" + location + "\'");
		} else {
			// location = "ws://localhost:8887";
			System.out.println("Default server url not specified: defaulting to \'" + location + "\'");
		}

		new ExampleWebsocketClient(location);
	}
}
