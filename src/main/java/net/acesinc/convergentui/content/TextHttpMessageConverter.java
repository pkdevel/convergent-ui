package net.acesinc.convergentui.content;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public class TextHttpMessageConverter extends AbstractHttpMessageConverter<String> {
	
	@Override
	public boolean canRead(final Class<?> type, final MediaType mt) {
		return mt != null && ("text".equalsIgnoreCase(mt.getType()) || mt.getSubtype() != null && mt.getSubtype().contains("javascript"));
	}
	
	@Override
	protected boolean supports(final Class<?> type) {
		// I don't believe this is actually used because we overrode canRead
		return true;
	}
	
	@Override
	protected void writeInternal(final String t, final HttpOutputMessage hom) throws IOException, HttpMessageNotWritableException {
		throw new UnsupportedOperationException("Not supported yet.");
	}
	
	@Override
	protected String readInternal(
			final Class<? extends String> type,
			final HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
		return convertStreamToString(inputMessage.getBody());
	}
	
	private static String convertStreamToString(final InputStream is) {
		try (final Scanner s = new Scanner(is)) {
			s.useDelimiter("\\A");
			return s.hasNext() ? s.next() : "";
		}
	}
}
