/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;

import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.util.Pair;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.constants.ZuulConstants;
import com.netflix.zuul.context.RequestContext;

/**
 * A majority of the boilerplate code was stolen from the SendResponseFilter in
 * the spring-cloud-netflix project:
 * https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-core/src/main/java/org/springframework/cloud/netflix/zuul/filters/post/SendResponseFilter.java
 *
 * @author andrewserff
 */
public abstract class BaseFilter extends ZuulFilter {
	
	private static final DynamicBooleanProperty INCLUDE_DEBUG_HEADER = DynamicPropertyFactory
			.getInstance()
			.getBooleanProperty(ZuulConstants.ZUUL_INCLUDE_DEBUG_HEADER, false);
	
	private static final DynamicBooleanProperty SET_CONTENT_LENGTH = DynamicPropertyFactory
			.getInstance()
			.getBooleanProperty(ZuulConstants.ZUUL_SET_CONTENT_LENGTH, false);
	
	protected static boolean isConvergentUIRequest(final HttpServletRequest request) {
		return request.getRequestURI().startsWith("/cui-req://");
	}
	
	protected static String getVerb(final HttpServletRequest request) {
		if (request != null && request.getMethod() != null) {
			return request.getMethod();
		}
		return "GET";
	}
	
	protected static MimeType getMimeType(final RequestContext context) {
		final List<Pair<String, String>> headers = context.getZuulResponseHeaders();
		for (final Pair<String, String> pair : headers) {
			if ("content-type".equalsIgnoreCase(pair.first())) {
				return MimeType.valueOf(pair.second());
			}
		}
		return null;
	}
	
	protected static String getContentType(final RequestContext context) {
		final MimeType type = getMimeType(context);
		if (type != null) {
			return type.getType() + "/" + type.getSubtype();
		}
		return "unknown";
	}
	
	protected static void writeResponse(final String responseBody, final MimeType contentType) throws Exception {
		if (responseBody == null || responseBody.isEmpty()) {
			return;
		}
		
		final RequestContext context = RequestContext.getCurrentContext();
		final HttpServletResponse servletResponse = context.getResponse();
		servletResponse.setCharacterEncoding(StandardCharsets.UTF_8.displayName());
		servletResponse.setContentType(contentType.toString());
		
		try (final OutputStream outStream = servletResponse.getOutputStream()) {
			writeResponse(new ByteArrayInputStream(responseBody.getBytes()), outStream);
		}
	}
	
	protected static void writeResponse(final BufferedImage image, final MediaType mediaType) throws Exception {
		if (image == null) {
			return;
		}
		
		final RequestContext context = RequestContext.getCurrentContext();
		final HttpServletResponse servletResponse = context.getResponse();
		servletResponse.setContentType(mediaType.toString());
		
		try (final ByteArrayOutputStream imageStream = new ByteArrayOutputStream()) {
			ImageIO.write(image, mediaType.getSubtype(), imageStream);
			servletResponse.setContentLength(imageStream.size());
			
			ImageIO.re
			
			try (final OutputStream outStream = servletResponse.getOutputStream()) {
				
				
				IOUtils.copy(imageStream, outStream);
				
				try (final InputStream is = new ByteArrayInputStream(imageStream.toByteArray())) {
					writeResponse(is, outStream);
				}
				outStream.flush();
			}
		}
	}
	
	private static void writeResponse(final InputStream input, final OutputStream output) throws Exception {
		IOUtils.copy(input, output);
	}
	
	protected static void addResponseHeaders() {
		final RequestContext context = RequestContext.getCurrentContext();
		final HttpServletResponse servletResponse = context.getResponse();
		final List<Pair<String, String>> zuulResponseHeaders = context.getZuulResponseHeaders();
		@SuppressWarnings("unchecked")
		final List<String> rd = (List<String>) RequestContext.getCurrentContext().get(
				"routingDebug");
		if (rd != null) {
			final StringBuilder debugHeader = new StringBuilder();
			for (final String it : rd) {
				debugHeader.append("[[[" + it + "]]]");
			}
			if (INCLUDE_DEBUG_HEADER.get()) {
				servletResponse.addHeader("X-Zuul-Debug-Header", debugHeader.toString());
			}
		}
		if (zuulResponseHeaders != null) {
			for (final Pair<String, String> it : zuulResponseHeaders) {
				servletResponse.addHeader(it.first(), it.second());
			}
		}
		final RequestContext ctx = RequestContext.getCurrentContext();
		final Long contentLength = ctx.getOriginContentLength();
		// Only inserts Content-Length if origin provides it and origin response is not gzipped
		if (SET_CONTENT_LENGTH.get()) {
			if (contentLength != null && !ctx.getResponseGZipped()) {
				servletResponse.setContentLengthLong(contentLength.longValue());
			}
		}
	}
}
