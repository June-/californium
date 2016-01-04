package org.eclipse.californium.examples;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.proxy.HttpTranslator;
import org.eclipse.californium.proxy.RequestHandler;
/*
 * 
 */
public class WebsocketStack {
	
	private static final Logger LOGGER = Logger.getLogger(WebsocketStack.class.getCanonicalName());
	
	private static final Response Response_NULL = new Response(null); 
	private static final int SOCKET_TIMEOUT = NetworkConfig.getStandard()
			.getInt(NetworkConfig.Keys.HTTP_SERVER_SOCKET_TIMEOUT);
	private static final int GATEWAY_TIMEOUT = SOCKET_TIMEOUT * 3 / 4;
	
	// ConcurrentHashMap
	private final ConcurrentHashMap<Request, Exchanger<Response>> exchangeMap = new ConcurrentHashMap<Request, Exchanger<Response>>();
	
	// 处理翻译得到的 coap 请求，可以由外部类来 set
	private RequestHandler requestHandler;
	
		
	public WebsocketStack(int wsPort) throws IOException {
		new WebsocketServer(wsPort);
	}

	/*
	 * 好像暂时不是很重要，
	 * 只有当 ProxyWebsocketServer 上有缓存时，才会真正调用到这个函数，通过下面这句：
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

	// 生产者线程
	private final class CoapRequestWorker extends Thread {
		private final Request coapRequest;
		
		public CoapRequestWorker(String name, Request coapRequest) 
		{ super(name);  this.coapRequest = coapRequest; }
		
		@Override
		public void run() {
			doReceiveMessage(coapRequest);
		}
	}
	
	// 消费者线程
	private final class CoapResponseWorker extends Thread {
		private final Request coapRequest;
		private final Thread requestWorker;

		public CoapResponseWorker(String name, Request coapRequest, 
				String place_holder,
				Thread requestWorker) {
			super(name);
			this.coapRequest = coapRequest;
			this.requestWorker = requestWorker;
		}
		
		public void run() {
			Exchanger<Response> exchanger = exchangeMap.get(coapRequest);
			
			if (exchanger == null) {
				LOGGER.warning("exchanger == null");
				// sendSimpleHttpResponse(httpExchange, HttpStatus.SC_INTERNAL_SERVER_ERROR);
				return;
			}
			
			Response coapResponse = null;
			try {
				coapResponse = exchanger.exchange(Response_NULL, GATEWAY_TIMEOUT, TimeUnit.MILLISECONDS);	
			} catch (Exception e) {
				/*
				 * TODO
				 * 稍微详细的异常处理
				 */
				e.printStackTrace();
			} finally {
				exchangeMap.remove(coapRequest);
				
				requestWorker.interrupt();
				
				LOGGER.fine("Entry 已从 Map 中移除");
			}
			
			if (coapResponse == null) {
				LOGGER.warning("没有相应的 coap 资源");
				// sendSimpleHttpResponse(httpExchange, HttpTranslator.STATUS_NOT_FOUND);
				return;
			}
			
			/*
			 * 取出响应并提交
			 * 
			 * HttpResponse httpResponse = httpExchange.getResponse();
			 * HttpTranslator.getHttpResponse(httpRequest, coapResponse, httpResponse);
			 * httpExchange.submitResponse();
			 */
			

		}
	}
	
	private class WebsocketServer {

		/*
		 * HttpStack 再这里做了许多工作 ——
		 * SyncBasicHttpParams 设参数
		 * HttpRequestInterceptor, HttpResponseInterceptor, HttpProcessor 
		 * 几个处理的类
		 * HttpAsyncRequestHandlerRegistry 注册异步处理
		 * ProxyAsyncRequestHandler, BasicAsyncRequestHandler, HttpAsyncService 
		 * NHttpConnectionFactory HTTP"连接池"？
		 */		
		public WebsocketServer(int wsPort) {
			/*
			 * 怎么做？
			 */
			
			
			
		}

		// private class BaseRequestHandler implements HttpRequestHandler {}

		// private class ProxyAsyncRequestHandler implements HttpAsyncRequestHandler<HttpRequest> {}
	}

	
	
	
	
	
 void doReceiveMessage(Request request) {
		requestHandler.handleRequest(request);
	}
	
	public RequestHandler getRequestHandler() 
	{ return requestHandler; }
	
	public void setRequestHandler(RequestHandler requestHandler) 
	{ this.requestHandler = requestHandler; }

}
