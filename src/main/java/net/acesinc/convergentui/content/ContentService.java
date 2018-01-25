/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui.content;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipException;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.netflix.zuul.constants.ZuulHeaders;
import com.netflix.zuul.context.RequestContext;

/**
 * The Content Service is responsible for getting all the content.
 * It uses a Caching Manager to cache the content from the downstream servers if
 * possible. It also leverages our ContentFetcherCommand which is a
 * Hystrix Circuit Breaker enabled content fetcher. This fetcher is what goes to
 * the actual live service to get the content either on the first try, or when the
 * cache is empty for the specified cacheName.
 * 
 * @author andrewserff
 */
@Service
public class ContentService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentService.class);
	
	private final RestTemplate restTemplate;
	
	@Autowired
	public ContentService(final RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
		this.restTemplate.getMessageConverters().add(new BufferedImageHttpMessageConverter());
		this.restTemplate.getMessageConverters().add(new TextHttpMessageConverter());
	}
	
	/**
	 * This is just a pass through/helper method for the full getContentFromService.
	 * 
	 * @param location
	 *            The remote location of the content
	 * @param cacheName
	 *            The unique cache name for this piece of content
	 * @param context
	 *            THe RequestContext for this request
	 * @return The content returned from the service as a string.
	 */
	public ContentResponse getContentFromService(final String location, final String cacheName, final RequestContext context) {
		return this.getContentFromService(location, cacheName, true, context);
	}
	
	/**
	 * This method will attempt to get the content from the named location.
	 * Important things to know about the location: - The location should
	 * contain a service name as the host. This service name will be resolved in
	 * the Eureka Discovery Service. - We use caching. We will first attempt to
	 * get the content out of the cache and it if is not there, we will ask the
	 * live service for the content. - You can disable caching by passing false
	 * for the useCache - When we get live content, we use a Hystrix Circuit
	 * Breaker. If the request fails, we return an empty response.
	 *
	 * @param location
	 *            The remote location of the content
	 * @param cacheName
	 *            The unique cache name for this piece of content
	 * @param useCache
	 *            Use caching if true, skip otherwise.
	 * @param context
	 *            THe RequestContext for this request
	 * @return The content returned from the service as a string.
	 */
	@Cacheable(value = "service-content", key = "#cacheName", unless = "!#useCache or #result.error")
	public ContentResponse getContentFromService(
			final String location,
			final String cacheName,
			final boolean useCache,
			final RequestContext context) {
		final ContentFetchCommand fetcher = new ContentFetchCommand(location, context, this.restTemplate);
		return fetcher.execute();
	}
	
	public static String getDownstreamResponse() {
		final RequestContext context = RequestContext.getCurrentContext();
		if (context.getResponseBody() == null && context.getResponseDataStream() == null) {
			return null;
		}
		
		if (RequestContext.getCurrentContext().getResponseBody() != null) {
			return RequestContext.getCurrentContext().getResponseBody();
		}
		try {
			final HttpServletResponse servletResponse = context.getResponse();
			servletResponse.setCharacterEncoding("UTF-8");
			
			boolean isGzipRequested = false;
			final String requestEncoding = context.getRequest().getHeader(ZuulHeaders.ACCEPT_ENCODING);
			if (requestEncoding != null && requestEncoding.equals("gzip")) {
				isGzipRequested = true;
			}
			
			final InputStream is = context.getResponseDataStream();
			InputStream inputStream = is;
			if (is != null) {
				if (context.sendZuulResponse()) {
					// if origin response is gzipped, and client has not requested gzip,
					// decompress stream
					// before sending to client
					// else, stream gzip directly to client
					if (context.getResponseGZipped() && !isGzipRequested) {
						try {
							inputStream = new GZIPInputStream(is);
						}
						catch (final ZipException ex) {
							LOGGER.error(
									"gzip expected but not received, assuming unencoded response: {}",
									RequestContext.getCurrentContext().getRequest().getRequestURL());
							inputStream = is;
						}
					}
					else if (context.getResponseGZipped() && isGzipRequested) {
						servletResponse.setHeader(ZuulHeaders.CONTENT_ENCODING, "gzip");
					}
					return IOUtils.toString(inputStream, StandardCharsets.UTF_8.displayName());
				}
			}
		}
		catch (final IOException ioe) {
			LOGGER.warn("IOException getting output stream", ioe);
		}
		
		return null;
	}
}
