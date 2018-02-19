/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui.content;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.netflix.zuul.filters.ProxyRequestHelper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.zuul.context.RequestContext;

/**
 * @author andrewserff
 */
public class ContentFetchCommand extends HystrixCommand<ContentResponse> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentFetchCommand.class);
	
	private final RestTemplate restTemplate;
	
	private final ProxyRequestHelper helper;
	
	private final String location;
	
	private final RequestContext requestContext;
	
	public ContentFetchCommand(final String location, final RequestContext requestContext, final RestTemplate restTemplate) {
		super(HystrixCommandGroupKey.Factory.asKey("content-fetch"));
		this.location = location;
		this.requestContext = requestContext;
		this.restTemplate = restTemplate;
		this.helper = new ProxyRequestHelper();
	}
	
	@Override
	protected ContentResponse run() throws Exception {
		LOGGER.debug("Getting live content from [ {} ]", this.location);
		try {
			final HttpServletRequest request = this.requestContext.getRequest();
			final MultiValueMap<String, String> headers = this.helper.buildZuulRequestHeaders(request);
			
			// if (request.getQueryString() != null && !request.getQueryString().isEmpty()) {
			// final MultiValueMap<String, String> params = this.helper.buildZuulRequestQueryParams(request);
			// }
			
			final HttpHeaders requestHeaders = new HttpHeaders();
			for (final String key : headers.keySet()) {
				for (final String s : headers.get(key)) {
					requestHeaders.add(key, s);
				}
			}
			final HttpEntity<Object> requestEntity = new HttpEntity<>(null, requestHeaders);
			final ResponseEntity<Object> exchange = this.restTemplate.exchange(this.location, HttpMethod.GET, requestEntity, Object.class);
			
			final ContentResponse response = new ContentResponse();
			response.setContent(exchange.getBody());
			response.setContentType(exchange.getHeaders().getContentType());
			response.setError(false);
			
			return response;
		}
		catch (final Exception e) {
			LOGGER.error("Error fetching live content from [ {} ]", this.location, e);
			throw e;
		}
	}
	
	@Override
	protected ContentResponse getFallback() {
		LOGGER.debug("ContentFetch failed for [ {} ]. Returing fallback response", this.location);
		final ContentResponse response = new ContentResponse();
		response.setContent("");
		response.setError(true);
		
		String defaultErrorMsg = this.getFailedExecutionException().getMessage();
		if (defaultErrorMsg == null || defaultErrorMsg.isEmpty()) {
			defaultErrorMsg = "Unknown Error";
		}
		// see if we can get a better error message
		final Exception errorFromThrowable = this.getExceptionFromThrowable(this.getExecutionException());
		final String errMessage = errorFromThrowable != null ? errorFromThrowable.getMessage() : defaultErrorMsg;
		
		response.setMessage(errMessage);
		return response;
	}
}
