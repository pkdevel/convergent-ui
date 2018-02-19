package net.acesinc.convergentui;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.zuul.context.RequestContext;

import static java.util.Arrays.asList;

import net.acesinc.convergentui.content.ContentResponse;
import net.acesinc.convergentui.content.ContentService;

@Component
public class ConvergentUIResponseFilter extends BaseResponseFilter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConvergentUIResponseFilter.class);
	
	private static final Collection<Tuple> SERVICE_LOCATIONS = asList(
			Tuple.of("img", "src"),
			Tuple.of("script", "src"),
			Tuple.of("link", "href"));
	
	private final ContentService contentManager;
	
	@Autowired
	public ConvergentUIResponseFilter(final ContentService contentManager) {
		this.contentManager = contentManager;
	}
	
	@Override
	public Object run() {
		final String origBody = ContentService.getDownstreamResponse();
		if (origBody == null || origBody.isEmpty()) {
			return null;
		}
		
		String composedBody = null;
		LOGGER.trace("Response from downstream server: {}", origBody);
		
		final Document doc = Jsoup.parse(origBody);
		if (hasReplaceableElements(doc)) {
			LOGGER.debug("We have replaceable elements. Let's get em!");
			final Elements elementsToUpdate = doc.select("div[data-loc]");
			for (final Element e : elementsToUpdate) {
				final StringBuilder content = new StringBuilder();
				final String location = e.dataset().get("loc");
				final String cacheName = e.dataset().get("cache-name");
				final boolean useCaching = !Boolean.parseBoolean(e.dataset().get("disable-caching"));
				final boolean failQuietly = Boolean.parseBoolean(e.dataset().get("fail-quietly"));
				
				try {
					// validation of service location uri
					final URL url = new URL(location);
					
					LOGGER.debug("Fetching content at location [ {} ] with cacheName = [ {} ]", location, cacheName);
					
					try {
						final RequestContext context = RequestContext.getCurrentContext();
						final ContentResponse response =
								this.contentManager.getContentFromService(location, cacheName, useCaching, context);
						
						LOGGER.trace(response.toString());
						
						if (!response.isError()) {
							final Object resp = response.getContent();
							if (String.class.isAssignableFrom(resp.getClass())) {
								final String subContentResponse = (String) resp;
								// TODO You better trust the source of your downstream HTML!
								// String cleanedContent = Jsoup.clean(subContentResponse, Whitelist.basic());
								// this totally stripped the html out...
								final Document subDocument = Jsoup.parse(subContentResponse);
								
								final String fragmentName = e.dataset().get("fragment-name");
								if (fragmentName != null) {
									final Elements fragments = subDocument.select("div[data-fragment-name=\"" + fragmentName + "\"]");
									
									if (fragments != null && fragments.size() > 0) {
										replaceRelativeRefs(fragments, url);
										
										if (fragments.size() == 1) {
											content.append(fragments.first().toString());
										}
										else {
											for (final Element frag : fragments) {
												content.append(frag.toString()).append("\n\n");
											}
										}
									}
									else {
										LOGGER.debug("Found no matching fragments for [ {} ]", fragmentName);
										if (failQuietly) {
											content.append("<div class='cui-error'></div>");
										}
										else {
											content.append("<span class='cui-error'>")
													.append("Failed getting content from remote service. Possible reason in reponse below")
													.append("</span>")
													.append(subDocument.toString());
										}
									}
								}
								else {
									final Elements dom = subDocument.select("html");
									replaceRelativeRefs(dom, url);
									
									// take the whole thing and cram it in there!
									content.append(subDocument.toString());
								}
							}
							else {
								// not text...
								if (!failQuietly) {
									content.append("<span class='cui-error'>")
											.append("Failed getting content from remote service. Reason: content was not text")
											.append("</span>");
								}
								else {
									content.append("<div class='cui-error'></div>");
								}
							}
						}
						else {
							if (!failQuietly) {
								content.append("<span class='cui-error'>Failed getting content from remote service. Reason: ")
										.append(response.getMessage())
										.append("</span>");
							}
							else {
								content.append("<div class='cui-error'></div>");
							}
						}
						
						// now append it to the page
						if (!content.toString().isEmpty()) {
							e.html(content.toString());
						}
					}
					catch (final Throwable t) {
						if (!failQuietly) {
							e.html(
									"<span class='cui-error'>Failed getting content from remote service. Reason: "
											+ t.getMessage()
											+ "</span>");
						}
						LOGGER.warn("Failed replacing content", t);
					}
					
					final String replaceOuterValue = e.dataset().get("replace-outer");
					final boolean replaceOuter = replaceOuterValue == null || Boolean.parseBoolean(replaceOuterValue);
					if (replaceOuter) {
						// outer element should be replaced by content
						e.unwrap();
					}
				}
				catch (final MalformedURLException ex) {
					LOGGER.warn("location was invalid: [ {} ]", location, ex);
					if (!failQuietly) {
						content.append("<span class='cui-error'>")
								.append("Failed getting content from remote service. Reason: data-loc was an invalid location.")
								.append("</span>");
					}
					else {
						content.append("<div class='cui-error'></div>");
					}
				}
			}
			
			composedBody = doc.toString();
		}
		else {
			LOGGER.debug("Document has no replaceable elements. Skipping");
		}
		
		try {
			addResponseHeaders();
			writeResponse(StringUtils.defaultIfEmpty(composedBody, origBody), getMimeType(RequestContext.getCurrentContext()));
		}
		catch (final Exception ex) {
			LOGGER.error("Error sending response", ex);
		}
		return null;
	}
	
	private static void replaceRelativeRefs(final Elements fragments, final URL url) {
		final Element frag = fragments.first();
		
		for (final Tuple sl : SERVICE_LOCATIONS) {
			// need to see if there are node attributes that we need to replace the urls on
			final Elements elements = frag.select(sl.node);
			for (final Element element : elements) {
				final String src = element.attr(sl.attribute);
				if (src.startsWith("/") && !src.startsWith("//")) {
					final String uri = buildServiceUri(url, src);
					element.attr(sl.attribute, uri);
					
					LOGGER.trace("Replacing relative reference '{}' with service location '{}'", src, uri);
				}
			}
		}
	}
	
	private static String buildServiceUri(final URL url, final String sourceRef) {
		return new StringBuilder()
				.append("/cui-req://")
				.append(url.getProtocol())
				.append("://")
				.append(url.getHost())
				.append(sourceRef)
				.toString();
	}
	
	protected static boolean hasReplaceableElements(final Document doc) {
		return doc.select("div[data-loc]").size() > 0;
	}
	
	static final class Tuple {
		
		String node, attribute;
		
		private Tuple(final String node, final String attribute) {
			this.node = node;
			this.attribute = attribute;
		}
		
		static Tuple of(final String node, final String attribute) {
			return new Tuple(node, attribute);
		}
	}
}
