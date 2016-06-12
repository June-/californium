package org.eclipse.californium.examples;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.EmptyMessage;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.Exchange.Origin;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.network.serialization.DataParser;
import org.eclipse.californium.core.network.serialization.DataSerializer;
import org.eclipse.californium.proxy.ProxyCoapResolver;
import org.eclipse.californium.proxy.resources.ProxyCacheResource;
import org.eclipse.californium.proxy.resources.StatsResource;
import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/*
 * Analog to ProxyHttpServer
 * 
 * 
 * 
 */
public class ProxyWebsocketServer extends WebSocketServer {
	private final static Logger LOGGER = Logger.getLogger(ProxyWebsocketServer.class.getCanonicalName());
	private static final String PROXY_COAP_CLIENT = "proxy/coapClient";
	private static final String PROXY_WS_CLIENT = "proxy/wsclient";
	// 缓存
	private final ProxyCacheResource cacheResource = new ProxyCacheResource(true);
	private final StatsResource statsResource = new StatsResource(cacheResource);

	// 作为 wsServer
	private WebsocketStack wsStack;

	// 作为 coapClient
	private ProxyCoapResolver proxyCoapResolver;

	// CARRIED OVER FROM WebsocketStack
	private static final Response Response_NULL = new Response(null);
	private static final int SOCKET_TIMEOUT = NetworkConfig.getStandard()
			.getInt(NetworkConfig.Keys.HTTP_SERVER_SOCKET_TIMEOUT);
	private static final int GATEWAY_TIMEOUT = SOCKET_TIMEOUT * 3 / 4;
	// ConcurrentHashMap
	private final ConcurrentHashMap<Request, Exchanger<Response>> exchangeMap = new ConcurrentHashMap<Request, Exchanger<Response>>();

	/*
	 * 
	 */
	public ProxyWebsocketServer(int wsPort) throws UnknownHostException {
		super(new InetSocketAddress(wsPort));
	}

	/*
	 * 好像暂时不是很重要，只有当 ProxyWebsocketServer 上有缓存时，才会真正调用到这个函数，通过下面这句：
	 * exchange.sendResponse(new Response(ResponseCode.BAD_OPTION));
	 */
	protected void doSendResponse(Request request, Response response) throws IOException {
		LOGGER.info("");
		Exchanger<Response> exchanger = exchangeMap.get(request);
		if (exchanger != null) {
			try {
				exchanger.exchange(response);
				LOGGER.info("（生产者）交换成功");
			} catch (InterruptedException e) {
				LOGGER.log(Level.WARNING, "交换被中断", e);
				exchangeMap.remove(request);
				return;
			}
		} else {
			LOGGER.warning("针对请求" + request + "的 exchanger 为空");
		}

	}

	/*
	 * Q. 这里 handleRequest() 被调用的过程？ A. handleRequest() 作为 HttpStack 的属性
	 * RequestHandler 所持有的一个方法，被 HttpStack.doReceiveMessage()
	 * 调用，doReceiveMessage() 又被线程类 CoapRequestWorker 的 run() 方法调用
	 * 
	 * Q. 这里的 Request 具体是那种类型的？ A. 是由 HTTP 请求翻译得到的 CoAP 请求， 翻译过程发生在
	 * HttpStack$HttpServer$ProxyAsyncRequestHandler handle()方法
	 * 
	 * Q. Exchange.sendResponse() 参数中的 response 是个什么东西？ A. 是从 Server 收到的真正的 coap
	 * 响应 稍后会被生产者 HttpStack$CoapRequestWorker 放在工厂里， 等待消费者
	 * HttpStack$CoapResponseWorker 取走
	 */
	public void handleRequest(final Request request) {

		//LOGGER.info("代理作为 WebsocketServer 收到这个请求：\n\t" + request + "\n");

		Exchange exchange = new Exchange(request, Origin.REMOTE) {

			@Override
			public void sendAccept() {
				// has no meaning for HTTP: do nothing
			}

			@Override
			public void sendReject() {
				// TODO: close the HTTP connection to signal rejection
			}

			@Override
			public void sendResponse(Response response) {
				try {
					request.setResponse(response);
					responseProduced(request, response);

					/*
					 * 这里还有问题，是不是这么写？？？
					 */
					doSendResponse(request, response);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		exchange.setRequest(request);

		Response response = null;
		// 若 request 为 reset 或 ack, 则忽略 ［代理会自行回复这个信息么？］
		// 检查有无 Proxy-Uri 选项
		if (request.getType() != Type.RST && request.getType() != Type.ACK && request.getOptions().hasProxyUri()) {

			response = cacheResource.getResponse(request); // 从缓存中获取 response

			LOGGER.info("（先看缓存有无数据）Cache returned " + response);

			statsResource.updateStatistics(request, response != null); // 更新数据
		}

		// 检查缓存中是否已有这个响应
		if (response != null) {
			/*
			 * 如果有缓存： 调用上文 exchange 重写了的 sendResponse() 方法
			 */
			LOGGER.info("之前请求过，缓存有数据，直接回复");
			exchange.sendResponse(response);
			return;
		} else {

			/*
			 * 如果没有缓存： 就交给 proxyCoapResolver 代为处理
			 */
			LOGGER.info("这是第一次请求，缓存无数据" + "\nrequest 内容为：\n" + request);
			

			if (request.getOptions().hasProxyUri()) {
			}
			
/*			try {
				manageProxyUriRequest(request);
				LOGGER.info("manageProxyUriRequest() 处理后的 request 为\n\t: " + request);
			} catch (URISyntaxException e) {
				LOGGER.warning(String.format("无效的 Proxy-Uri: %s", request.getOptions().getProxyUri()));
				exchange.sendResponse(new Response(ResponseCode.BAD_OPTION));
			}*/

			// ProxyCoapResolver 与真正的 Server 发生请求应答
			proxyCoapResolver.forwardRequest(exchange);

		}

	}

	/*
	 * 从 ProxyHttpServer 直接搬过来
	 * 
	 * （根据 scheme 把 request 翻译为合适的 Path, 使之能被正确转发）
	 */
	private void manageProxyUriRequest(Request request) throws URISyntaxException {

		URI proxyUri = new URI(request.getOptions().getProxyUri());

		String clientPath;

		if (proxyUri.getScheme() != null && proxyUri.getScheme().matches("^ws.*")) {
			clientPath = PROXY_WS_CLIENT;
		} else {
			clientPath = PROXY_COAP_CLIENT;
		}

		LOGGER.info("clientPath 选定为: " + clientPath);
		request.getOptions().setUriPath(clientPath);
	}

	/*
	 * 从 ProxyHttpServer 直接搬过来
	 */
	protected void responseProduced(Request request, Response response) {
		if (request.getOptions().hasProxyUri()) {
			LOGGER.info("缓存此响应");
			cacheResource.cacheResponse(request, response);
		} else {
			LOGGER.info("不缓存此响应");
		}
	}

	public ProxyCoapResolver getProxyCoapResolver() {
		return proxyCoapResolver;
	}

	public void setProxyCoapResolver(ProxyCoapResolver proxyCoapResolver) {
		this.proxyCoapResolver = proxyCoapResolver;
	}

	// 生产者线程
	private final class CoapRequestWorker extends Thread {
		private final Request coapRequest;

		public CoapRequestWorker(String name, Request coapRequest) {
			super(name);
			this.coapRequest = coapRequest;
		}

		@Override
		public void run() {
			handleRequest(coapRequest);
		}
	}

	// 消费者线程
	private final class CoapResponseWorker extends Thread {
		private final Request coapRequest;
		private final Thread requestWorker;
		private WebSocket conn; // 可以是 final 么？？？

		public CoapResponseWorker(String name, WebSocket conn, Request coapRequest, Thread requestWorker) {
			super(name);
			this.conn = conn;
			this.coapRequest = coapRequest;
			this.requestWorker = requestWorker;
		}

		/*
		 * 先用 Cf 的一套 exchange 得到 coapResponse; 
		 * 再由 WebSocket 发送函数回复给 Client端
		 */
		public void run() {
			// 获取 exchanger
			Exchanger<Response> exchanger = exchangeMap.get(coapRequest);

			if (exchanger == null) {
				LOGGER.warning("exchanger == null");
				// sendSimpleHttpResponse(httpExchange,
				// HttpStatus.SC_INTERNAL_SERVER_ERROR);
				return;
			}

			// 获取响应
			Response coapResponse = null;
			try {
				coapResponse = exchanger.exchange(Response_NULL, GATEWAY_TIMEOUT, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				/*
				 * TODO 稍微详细的异常处理
				 */
				//e.printStackTrace();
			} finally {
				exchangeMap.remove(coapRequest);

				requestWorker.interrupt();

				LOGGER.fine("Entry 已从 Map 中移除");
			}

			if (coapResponse == null) {
				LOGGER.warning("所请求的 coap 资源不存在");
				conn.send("所请求的 coap 资源不存在");
				// sendSimpleHttpResponse(httpExchange,
				// HttpTranslator.STATUS_NOT_FOUND);
				return;
			}

			if (conn.isOpen()) {
				byte[] outgoingResponse = new DataSerializer().serializeResponse(coapResponse);
				LOGGER.info("正在将 coapResponse 回复给 Client 端\n\t: " + coapResponse);
				try {
				conn.send(outgoingResponse);
				} catch(WebsocketNotConnectedException e) {
					LOGGER.info(conn + "关闭了连接！");
				}
			}
		}
	}

	/*
	 * 以下为 WebSocket 事件处理函数
	 */
	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		// LOGGER.info("建立了新的websocket连接:\n" + conn.getRemoteSocketAddress().getAddress().getHostAddress());
		// LOGGER.info("建立了新的websocket连接: " + conn.getRemoteSocketAddress().getAddress());
		LOGGER.info("接受了新的websocket连接: " + conn.getRemoteSocketAddress());

		conn.send("已连接，这是一条服务器主动发起的的消息");
		LOGGER.info("已连接，这是一条服务器主动发起的的消息");
	}

	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		LOGGER.info(conn + "关闭了连接！");
		conn.close();
	}

	@Override
	public void onMessage(WebSocket conn, ByteBuffer message) {
		
		LOGGER.info("收到ByteBuffer: " + message);

		DataParser ds = new DataParser(message.array());
				
		if (ds.isRequest()) {
			Request coapRequest = ds.parseRequest();
			LOGGER.info("收到作为 WebSocket payload 的 coap 请求：\n\t" + coapRequest + "\n");

			exchangeMap.put(coapRequest, new Exchanger<Response>());

			Thread requestWorker = new CoapRequestWorker("requestWorker: 生产者", coapRequest);
			Thread responseWorker = new CoapResponseWorker("responseWorker: 消费者", conn, coapRequest, requestWorker);
			requestWorker.start();
			responseWorker.start();
			LOGGER.fine("启动 requestWorker, responseWorker 线程以接收响应消息");
		} else if (ds.isEmpty()) {
			EmptyMessage em = ds.parseEmptyMessage();
			LOGGER.info("收到作为 WebSocket payload 的 coap 空消息：\n\t" + em + "\n");

		}

		/*
		 * 未作异常处理！！！
		 */
	}

	@Override
	public void onError(WebSocket conn, Exception ex) {
		ex.printStackTrace();
		if (conn != null) {
			// some errors like port binding failed may not be assignable to a
			// specific websocket
		}
	}

	@Override
	public void onMessage(WebSocket conn, String message) {
		LOGGER.info("收到String: " + message);
	}

	public static void main(String[] args) throws InterruptedException, IOException {
		WebSocketImpl.DEBUG = true;
		int port = 8887;
		ProxyWebsocketServer proxyWS = new ProxyWebsocketServer(port);
		proxyWS.start();
		LOGGER.info("代理已启动，端口号：" + proxyWS.getPort());

	}

}
