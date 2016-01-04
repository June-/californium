package org.eclipse.californium.examples;

import java.io.IOException;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.proxy.DirectProxyCoapResolver;
import org.eclipse.californium.proxy.resources.ForwardingResource;
import org.eclipse.californium.proxy.resources.ProxyCoapClientResource;
import org.java_websocket.WebSocketImpl;

public class ExampleCrossProxy_924 {

		private static final int PORT = NetworkConfig.getStandard().getInt(NetworkConfig.Keys.COAP_PORT);
		
		private CoapServer targetServerA;
		
		
		public ExampleCrossProxy_924() throws IOException {
			ForwardingResource coap2coap = new ProxyCoapClientResource("coap2coap");
			
			// 模拟一个 coap服务端，并添加资源
			targetServerA = new CoapServer(PORT);
			targetServerA.add(new TargetResource("target"));
			targetServerA.add(new SeperateResponseResource("seperateResponse"));
			targetServerA.start();
			
			
			/* 设置和启动代理
			 * （与ProxyHttpServer不同，这里需要一个 start() 步骤，启动 ProxyWebsocketServer）
			 */
			// WebSocketImpl.DEBUG = true;
			int port = 8887;
			ProxyWebsocketServer wsServer = new ProxyWebsocketServer(port);
			wsServer.setProxyCoapResolver(new DirectProxyCoapResolver(coap2coap));
			wsServer.start();
			System.out.println("CoAP resource \"模拟目标资源\" available over WebSocket at: "
					+ "coap://localhost:PORT/seperateResponse");

		}

		
		// 模拟一个简单资源，对收到的GET请求，回复一条包含资源名字的响应消息
		private static class TargetResource extends CoapResource {
			private int counter = 0;
			
			public TargetResource(String name) {
				super(name);
			}
			
			@Override 
			public void handleGET(CoapExchange exchange) {
				exchange.respond("Response " + ++counter + " from resource " + getName());
			}
			
		}		
		
		private static class SeperateResponseResource extends CoapResource {
			private int counter = 1;
			
			public SeperateResponseResource(String name) {
				super(name);
			}
			
			@Override
			public void handleGET(CoapExchange exchange) {
				
				exchange.accept();
				
				exchange.respond("Seperate response " + counter++);
			}
		}
		
		
		
		public static void main(String[] args) throws Exception {
			new ExampleCrossProxy_924();
		}
}
