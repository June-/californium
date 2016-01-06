/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and re-implementation
 *    Francesco Corazza - HTTP cross-proxy
 ******************************************************************************/
package org.eclipse.californium.proxy.resources;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.proxy.CoapTranslator;
import org.eclipse.californium.proxy.TranslationException;


/**
 * Resource that forwards a coap request with the proxy-uri option set to the
 * desired coap server.
 */
public class ProxyCoapClientResource extends ForwardingResource {
	
	public ProxyCoapClientResource() {
		this("coapClient");
	} 
	
	public ProxyCoapClientResource(String name) {
		// set the resource hidden
		super(name, true);
		getAttributes().setTitle("Forward the requests to a CoAP server.");
	}

	@Override
	public Response forwardRequest(Request request) {
		LOGGER.info("ProxyCoAP2CoAP forwards \n\t"+request);
		//Request incomingRequest = request;

		// check the invariant: the request must have the proxy-uri set
/*		if (!incomingRequest.getOptions().hasProxyUri()) {
			LOGGER.warning("Proxy-uri option not set.");
			return new Response(ResponseCode.BAD_OPTION);
		}
*/
		// remove the fake uri-path
		// FIXME: HACK // TODO: why? still necessary in new Cf?
//		incomingRequest.getOptions().clearUriPath();

		// create a new request to forward to the requested coap server
		Request outgoingRequest = request;
/*	try {
			// create the new request from the original
			outgoingRequest = CoapTranslator.getRequest(incomingRequest);
			
			LOGGER.finer("\t翻译前的coap请求：" + incomingRequest + "\n\t翻译后的coap请求" + outgoingRequest);

		} catch (TranslationException e) {
			LOGGER.warning("Proxy-uri option malformed: " + e.getMessage());
			return new Response(CoapTranslator.STATUS_FIELD_MALFORMED);
		} catch (Exception e) {
			LOGGER.warning("Failed to execute request: " + e.getMessage());
			return new Response(ResponseCode.INTERNAL_SERVER_ERROR);
		}*/

		LOGGER.info("ProxyCoapClient received CoAP request and sends a copy to CoAP target");
		LOGGER.finer("Sending coap request.");
		outgoingRequest.setURI(outgoingRequest.getURI());
		outgoingRequest.send();

		// accept the request sending a separate response to avoid the
		// timeout in the requesting client
		LOGGER.finer("Acknowledge message sent");

		
		try {
			// receive the response // TODO: don't wait for ever
			Response receivedResponse = outgoingRequest.waitForResponse();

			if (receivedResponse != null) {
/*				LOGGER.finer("Coap response received.");

				// create the real response for the original request
				Response outgoingResponse = CoapTranslator.getResponse(receivedResponse);

				LOGGER.finer("\t翻译前的coap响应：" + receivedResponse + "\n\t翻译后的coap响应" + outgoingResponse);
				
				return outgoingResponse;*/
				return receivedResponse;
			} else {
				LOGGER.warning("No response received.");
				return new Response(CoapTranslator.STATUS_TIMEOUT);
			}
		} catch (InterruptedException e) {
			LOGGER.warning("Receiving of response interrupted: " + e.getMessage());
			return new Response(ResponseCode.INTERNAL_SERVER_ERROR);
		}
	}
}
