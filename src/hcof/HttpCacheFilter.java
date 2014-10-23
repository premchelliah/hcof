package hcof;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.apache.commons.io.FilenameUtils;

@WebFilter(filterName = "HttpCacheOptimizer", urlPatterns = { "/*" })
public class HttpCacheFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		final HttpServletRequest httpRequest = (HttpServletRequest) request;
		final HttpServletResponse httpResponse = (HttpServletResponse) response;

		if (canCacheInfinitely(httpRequest)) {

			HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(httpResponse) {

				@Override
				public void setHeader(String name, String value) {

					if (!"ETag".equals(name)) {
						httpResponse.setHeader(name, value);
					}
				}
			};
			httpResponse.setHeader("Cache-Control", "max-age=31536000, public"); // cache for a year
			chain.doFilter(request, responseWrapper);
		}
		else {

			chain.doFilter(request, response);
		}
	}

	private static boolean canCacheInfinitely(HttpServletRequest httpRequest) {

		String uri = httpRequest.getRequestURI();
		String temp = FilenameUtils.getName(uri);
		String s = FilenameUtils.getExtension(uri).toLowerCase();
		
		if (s.equals("js") || s.equals("gif") || s.equals("png") || s.equals("jpg") || s.equals("swf") || s.equals("bmp") || s.equals("ico")
				|| s.equals("flv") || s.equals("xml") || s.equals("css") || s.equals("pdf") || s.equals("rtf")) {

				int pos = temp.lastIndexOf('_');
				if(pos != -1 && temp.substring(pos+1).length() > 30) return true;
		}
		return false;
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
	}

	@Override
	public void init(FilterConfig arg0) throws ServletException {
		// TODO Auto-generated method stub
	}
}