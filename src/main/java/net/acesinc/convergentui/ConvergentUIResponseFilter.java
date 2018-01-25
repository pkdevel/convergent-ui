/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui;

import java.net.MalformedURLException;
import java.net.URL;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.zuul.context.RequestContext;

import net.acesinc.convergentui.content.ContentResponse;
import net.acesinc.convergentui.content.ContentService;

/**
 * The ConvergentUIResponseFilter handles the html coming back from your main
 * service and will replace content with your template.
 *
 * @author andrewserff
 */
@Component
public class ConvergentUIResponseFilter extends BaseResponseFilter {
	
	private static final Logger log = LoggerFactory.getLogger(ConvergentUIResponseFilter.class);
	
	@Autowired
	private ContentService contentManager;
	
	@Override
	public Object run() {
		
		final String origBody = this.contentManager.getDownstreamResponse();
		if (origBody == null || origBody.isEmpty()) {
			return null;
		}
		
		String composedBody = null;
		log.trace("Response from downstream server: " + origBody);
		
		final Document doc = Jsoup.parse(origBody);
		if (ConvergentUIResponseFilter.hasReplaceableElements(doc)) {
			log.debug("We have replaceable elements. Let's get em!");
			final Elements elementsToUpdate = doc.select("div[data-loc]");
			for (final Element e : elementsToUpdate) {
				final StringBuilder content = new StringBuilder();
				final String location = e.dataset().get("loc");
				final String fragmentName = e.dataset().get("fragment-name");
				final String cacheName = e.dataset().get("cache-name");
				final boolean useCaching = !Boolean.parseBoolean(e.dataset().get("disable-caching"));
				final boolean failQuietly = Boolean.parseBoolean(e.dataset().get("fail-quietly"));
				final boolean replaceOuter = e.dataset().get("replace-outer") == null
						? true
						: Boolean.parseBoolean(e.dataset().get("replace-outer"));
				
				URL url = null;
				try {
					url = new URL(location);
					final String protocol = url.getProtocol();
					final String service = url.getHost();
					
					log.debug("Fetching content at location [ " + location + " ] with cacheName = [ " + cacheName + " ]");
					
					try {
						final RequestContext context = RequestContext.getCurrentContext();
						final ContentResponse response =
								this.contentManager.getContentFromService(location, cacheName, useCaching, context);
						
						log.trace(response.toString());
						
						if (!response.isError()) {
							final Object resp = response.getContent();
							if (String.class.isAssignableFrom(resp.getClass())) {
								final String subContentResponse = (String) resp;
								// TODO You better trust the source of your downstream HTML!
								// String cleanedContent = Jsoup.clean(subContentResponse, Whitelist.basic());
								// this totally stripped the html out...
								final Document subDocument = Jsoup.parse(subContentResponse);
								
								if (fragmentName != null) {
									final Elements fragments = subDocument.select("div[data-fragment-name=\"" + fragmentName + "\"]");
									
									if (fragments != null && fragments.size() > 0) {
										if (fragments.size() == 1) {
											final Element frag = fragments.first();
											
											// need to see if there are images that we need to replace the urls on
											final Elements images = frag.select("img");
											for (final Element i : images) {
												final String src = i.attr("src");
												if (src.startsWith("/") && !src.startsWith("//")) {
													i.attr("src", "/cui-req://" + protocol + "://" + service + src);
												} // else what do we do about relative urls?
											}
											
											content.append(frag.toString());
											
										}
										else {
											for (final Element frag : fragments) {
												content.append(frag.toString()).append("\n\n");
											}
										}
									}
									else {
										log.debug("Found no matching fragments for [ " + fragmentName + " ]");
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
						log.warn("Failed replacing content", t);
					}
					
					if (replaceOuter) {
						// outer element should be replaced by content
						e.unwrap();
					}
				}
				catch (final MalformedURLException ex) {
					log.warn("location was invalid: [ " + location + " ]", ex);
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
			log.debug("Document has no replaeable elements. Skipping");
		}
		
		try {
			this.addResponseHeaders();
			if (composedBody != null && !composedBody.isEmpty()) {
				this.writeResponse(composedBody, this.getMimeType(RequestContext.getCurrentContext()));
			}
			else {
				this.writeResponse(origBody, this.getMimeType(RequestContext.getCurrentContext()));
			}
		}
		catch (final Exception ex) {
			log.error("Error sending response", ex);
			
		}
		return null;
	}
	
	protected static boolean hasReplaceableElements(final Document doc) {
		return doc.select("div[data-loc]").size() > 0;
	}
}
