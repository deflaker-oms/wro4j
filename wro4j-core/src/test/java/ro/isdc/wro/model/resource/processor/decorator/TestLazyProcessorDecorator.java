package ro.isdc.wro.model.resource.processor.decorator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.model.resource.processor.impl.js.JSMinProcessor;
import ro.isdc.wro.util.LazyInitializer;
import ro.isdc.wro.util.WroTestUtils;


/**
 * @author Alex Objelean
 */
public class TestLazyProcessorDecorator {
  @Mock
  private Reader mockReader;
  @Mock
  private Writer mockWriter;
  private ProcessorDecorator mockProcessor;

  private LazyProcessorDecorator victim;

  @Before
  public void setUp() {
    Context.set(Context.standaloneContext());
    MockitoAnnotations.initMocks(this);
    mockProcessor = Mockito.spy(new ProcessorDecorator(new JSMinProcessor()));
  }

  @Test(expected = NullPointerException.class)
  public void cannotProcessNullLazyProcessor()
      throws Exception {
    victim = new LazyProcessorDecorator(new LazyInitializer<ResourcePreProcessor>() {
      @Override
      protected ResourcePreProcessor initialize() {
        return null;
      }
    });
    WroTestUtils.createInjector().inject(victim);
    victim.process(null, mockReader, mockWriter);
  }

  @Test
  public void shouldInvokeLazyProcessor()
      throws Exception {
    final ResourceType expectedResourceType = ResourceType.CSS;
    when(mockProcessor.isMinimize()).thenReturn(true);
    when(mockProcessor.getSupportedResourceType()).thenReturn(new SupportedResourceType() {
      public Class<? extends Annotation> annotationType() {
        return SupportedResourceType.class;
      }
      public ResourceType value() {
        return expectedResourceType;
      }
    });
    when(mockProcessor.isSupported()).thenReturn(false);
    victim = new LazyProcessorDecorator(new LazyInitializer<ResourcePreProcessor>() {
      @Override
      protected ResourcePreProcessor initialize() {
        return mockProcessor;
      }
    });
    WroTestUtils.createInjector().inject(victim);
    victim.process(null, mockReader, mockWriter);
    assertTrue(victim.isMinimize());
    assertFalse(victim.isSupported());
    assertEquals(expectedResourceType, victim.getSupportedResourceType().value());
    verify(mockProcessor).process(null, mockReader, mockWriter);
  }
}
