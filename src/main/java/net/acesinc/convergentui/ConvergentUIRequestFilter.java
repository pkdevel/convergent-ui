package net.acesinc.convergentui;

import java.awt.image.BufferedImage;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.zuul.context.RequestContext;

import net.acesinc.convergentui.content.ContentResponse;
import net.acesinc.convergentui.content.ContentService;

@Component
public class ConvergentUIRequestFilter extends BaseRequestFilter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConvergentUIRequestFilter.class);
	
	private final ContentService contentManager;
	
	private final ObjectMapper mapper;
	
	@Autowired
	public ConvergentUIRequestFilter(final ContentService contentManager) {
		this.contentManager = contentManager;
		this.mapper = new ObjectMapper();
	}
	
	@Override
	public Object run() {
		final RequestContext context = RequestContext.getCurrentContext();
		final HttpServletRequest req = context.getRequest();
		final String path = req.getRequestURI();
		final String location = path.substring("/cui-req://".length());
		LOGGER.debug("RequestFilter for [ {} ] in process", location);
		
		final ContentResponse response = this.contentManager.getContentFromService(location, location, false, context);
		
		addResponseHeaders();
		
		if (!response.isError()) {
			final Object resp = response.getContent();
			try {
				final MimeType type = response.getContentType();
				if (String.class.isAssignableFrom(resp.getClass())) {
					writeResponse((String) resp, type);
				}
				else if (BufferedImage.class.isAssignableFrom(resp.getClass())) {
					writeResponse((BufferedImage) resp, response.getContentType());
				}
				else if (type.getSubtype().contains("json")) {
					writeResponse(this.mapper.writeValueAsString(resp), type);
				}
				else {
					LOGGER.warn(
							"Unknown response type [ {} ] that we can't handle yet. Content is of type: {}",
							response.getContentType(),
							resp.getClass());
				}
			}
			catch (final Exception ex) {
				LOGGER.error("Error writing response", ex);
			}
		}
		
		return null;
	}
}
