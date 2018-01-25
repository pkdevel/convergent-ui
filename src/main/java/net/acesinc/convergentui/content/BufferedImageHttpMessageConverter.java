/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.acesinc.convergentui.content;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/**
 * @author andrewserff
 */
public class BufferedImageHttpMessageConverter extends AbstractHttpMessageConverter<BufferedImage> {
	
	private static final Logger log = LoggerFactory.getLogger(BufferedImageHttpMessageConverter.class);
	
	@Override
	public boolean canRead(final Class<?> type, final MediaType mt) {
		log.debug("Can we read: Class[ " + type + "] & ContentType[ " + mt + " ]");
		
		return BufferedImage.class.isAssignableFrom(type) || mt != null && "image".equalsIgnoreCase(mt.getType());
	}
	
	@Override
	protected boolean supports(final Class<?> type) {
		// I don't believe this is actually used because we overrode canRead
		// return BufferedImage.class.isAssignableFrom(type);
		return true;
	}
	
	@Override
	protected BufferedImage readInternal(
			final Class<? extends BufferedImage> type,
			final HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
		return ImageIO.read(inputMessage.getBody());
	}
	
	@Override
	protected void writeInternal(final BufferedImage t, final HttpOutputMessage hom) throws IOException, HttpMessageNotWritableException {
		throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose Tools | Templates.
	}
}
