/**
 * Copyright Alex Objelean
 */
package ro.isdc.wro.processor.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.annot.Inject;
import ro.isdc.wro.annot.SupportedResourceType;
import ro.isdc.wro.processor.ResourcePostProcessor;
import ro.isdc.wro.processor.ResourcePreProcessor;
import ro.isdc.wro.resource.Resource;
import ro.isdc.wro.resource.ResourceType;
import ro.isdc.wro.resource.UriLocator;
import ro.isdc.wro.resource.UriLocatorFactory;
import ro.isdc.wro.util.StringUtils;
import ro.isdc.wro.util.WroUtil;


/**
 * CssImport Processor responsible for handling css @import statement. It is implemented as both: preProcessor &
 * postProcessor. It is necessary because preProcessor is responsible for updating model with found imported resources,
 * while post processor removes import occurences.
 *
 * @author Alex Objelean
 */
@SupportedResourceType(type=ResourceType.CSS)
public class CssImportProcessor
  implements ResourcePreProcessor, ResourcePostProcessor {
  /**
   * Logger for this class.
   */
  private static final Logger LOG = LoggerFactory.getLogger(CssImportProcessor.class);
  /**
   * Contains a {@link UriLocatorFactory} reference injected externally.
   */
  @Inject
  private UriLocatorFactory uriLocatorFactory;
  /** The url pattern */
  private static final Pattern PATTERN = Pattern.compile("@import\\s*url\\(\\s*"
    + "[\"']?([^\"']*)[\"']?" // any sequence of characters, except an unescaped ')'
    + "\\s*\\);?", // Any number of whitespaces, then ')'
    Pattern.CASE_INSENSITIVE); // works with 'URL('

  /**
   * Remove all @import occurences.
   */
  public void process(final Reader reader, final Writer writer)
    throws IOException {
    final Matcher m = PATTERN.matcher(IOUtils.toString(reader));
    final StringBuffer sb = new StringBuffer();
    while (m.find()) {
      //add and check if already exist
      m.appendReplacement(sb, "");
    }
    m.appendTail(sb);
    writer.write(sb.toString());
  }

  /**
   * {@inheritDoc}
   */
  public void process(final Resource resource, final Reader reader, final Writer writer)
    throws IOException {
    LOG.debug("PROCESS: " + resource);
    final String result = parseCss(resource, reader);
    writer.write(result);
    writer.close();
  }

  /**
   * Parse css, find all import statements.
   *
   * @param resource {@link Resource} where the parsed css resides.
   */
  private String parseCss(final Resource resource, final Reader reader) throws IOException {
    final StringBuffer sb = new StringBuffer();
    final Collection<Resource> importsCollector = new ArrayList<Resource>();
    parseImports(resource, importsCollector);
    //remove currently processing resource
    importsCollector.remove(resource);
    // prepend entire list of resources
    for (final Resource imported : importsCollector) {
      //TODO fix this & it shouldn't be needed.
      // we should skip this because there is no other way to be sure that the processor prepended already these
      // resources.
      if (resource.getGroup().getResources().contains(imported)) {
        break;
      }
      resource.prepend(imported);
    }
    if (!importsCollector.isEmpty()) {
      LOG.debug("Imported resources found : " + importsCollector.size());
    }

    sb.append(getResourceContent(resource));
    LOG.debug("" + importsCollector);
    return sb.toString();
  }

  /**
   * TODO: update javadoc
   */
  private void parseImports(final Resource resource, final Collection<Resource> resources)
    throws IOException {
    parseImports(resource, new Stack<Resource>(), resources);
  }

  /**
   * TODO update javadoc
   */
  private void parseImports(final Resource resource, final Stack<Resource> stack, final Collection<Resource> resources)
    throws IOException {
    if (resources.contains(resource) || stack.contains(resource)) {
      LOG.warn("RECURSIVITY detected for resource: " + resource);
      return;
    }
    LOG.debug("PUSH: " + resource);
    stack.push(resource);
    final Collection<Resource> imports = getImportedResources(resource);
    LOG.debug("IMPORT LIST: " + imports);
    for (final Resource imported : imports) {
      try {
        if (resource.equals(imported)) {
          LOG.warn("Recursivity detected for resource: " + resource);
        } else {
          parseImports(imported, stack, resources);
        }
      } catch (final IOException e) {
        // remove invalid uri
        stack.pop();
        LOG.warn("Invalid imported resource: " + imported + " located in: " + resource);
      }
    }
    final Resource r = stack.pop();
    LOG.debug("POP: " + r);
    resources.add(r);
  }

  /**
   * @return the content of the resource as string.
   */
  private String getResourceContent(final Resource resource)
    throws IOException {
    final UriLocator uriLocator = uriLocatorFactory.getInstance(resource.getUri());
    final Reader reader = new InputStreamReader(uriLocator.locate(resource.getUri()));
    return IOUtils.toString(reader);
  }

  /**
   * Find a set of imported resources inside a given resource.
   */
  private Collection<Resource> getImportedResources(final Resource resource) throws IOException {
    //it should be sorted
    final List<Resource> imports = new ArrayList<Resource>();
    //Check if @Scanner#findWithinHorizon can be used instead
    final String css = getResourceContent(resource);
    final Matcher m = PATTERN.matcher(css);
    while (m.find()) {
      final Resource importedResource = buildImportedResource(resource, m.group(1));
      // check if already exist
      if (imports.contains(importedResource)) {
        LOG.warn("Duplicate imported resource: " + importedResource);
      } else {
        imports.add(importedResource);
      }
    }
    return imports;
  }

  /**
   * Build a {@link Resource} object from a found importedResource inside a given resource.
   */
  private Resource buildImportedResource(final Resource resource, final String importUrl) {
    final String absoluteUrl = computeAbsoluteUrl(resource, importUrl);
    final Resource importResource = Resource.create(absoluteUrl, ResourceType.CSS);
    return importResource;
  }

	/**
	 * Computes absolute url of the imported resource.
	 *
	 * @param relativeResource {@link Resource} where the import statement is found.
	 * @param importUrl found import url.
	 * @return absolute url of the resource to import.
	 */
  private String computeAbsoluteUrl(final Resource relativeResource, final String importUrl) {
    final String folder = WroUtil.getFolderOfUri(relativeResource.getUri());
    //remove '../' & normalize the path.
    final String absoluteImportUrl = StringUtils.normalizePath(folder + importUrl);
    return absoluteImportUrl;
  }
}
