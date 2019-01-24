package datadog.opentracing.propagation

import datadog.opentracing.DDSpanContext
import datadog.opentracing.DDTracer
import datadog.opentracing.PendingTrace
import datadog.trace.api.sampling.PrioritySampling
import datadog.trace.common.writer.ListWriter
import io.opentracing.propagation.TextMapAdapter
import spock.lang.Shared
import spock.lang.Specification

import static datadog.opentracing.propagation.HTTPCodec.BIG_INTEGER_UINT64_MAX

class HTTPCodecTest extends Specification {
  @Shared
  private static final String OT_BAGGAGE_PREFIX = "ot-baggage-"
  @Shared
  private static final String TRACE_ID_KEY = "x-datadog-trace-id"
  @Shared
  private static final String SPAN_ID_KEY = "x-datadog-parent-id"
  @Shared
  private static final String SAMPLING_PRIORITY_KEY = "x-datadog-sampling-priority"

  HTTPCodec codec = new HTTPCodec(["SOME_HEADER": "some-tag"])

  def "inject http headers"() {
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        "1",
        "2",
        "0",
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, "1", [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == "1"
    carrier.get(SPAN_ID_KEY) == "2"
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with larger than Java long IDs"() {
    String largeTraceId = "9523372036854775807"
    String largeSpanId = "15815582334751494918"
    String largeParentId = "15815582334751494914"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == largeTraceId
    carrier.get(SPAN_ID_KEY) == largeSpanId
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "inject http headers with uint 64 max IDs"() {
    String largeTraceId = "18446744073709551615"
    String largeSpanId = "18446744073709551614"
    String largeParentId = "18446744073709551613"
    setup:
    def writer = new ListWriter()
    def tracer = new DDTracer(writer)
    final DDSpanContext mockedContext =
      new DDSpanContext(
        largeTraceId,
        largeSpanId,
        largeParentId,
        "fakeService",
        "fakeOperation",
        "fakeResource",
        samplingPriority,
        new HashMap<String, String>() {
          {
            put("k1", "v1")
            put("k2", "v2")
          }
        },
        false,
        "fakeType",
        null,
        new PendingTrace(tracer, largeTraceId, [:]),
        tracer)

    final Map<String, String> carrier = new HashMap<>()

    codec.inject(mockedContext, new TextMapAdapter(carrier))

    expect:
    carrier.get(TRACE_ID_KEY) == largeTraceId
    carrier.get(SPAN_ID_KEY) == largeSpanId
    carrier.get(SAMPLING_PRIORITY_KEY) == (samplingPriority == PrioritySampling.UNSET ? null : String.valueOf(samplingPriority))
    carrier.get(OT_BAGGAGE_PREFIX + "k1") == "v1"
    carrier.get(OT_BAGGAGE_PREFIX + "k2") == "v2"

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "1",
      (SPAN_ID_KEY.toUpperCase())             : "2",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = codec.extract(new TextMapAdapter(actual))

    expect:
    context.getTraceId() == "1"
    context.getSpanId() == "2"
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract header tags with no propagation"() {
    setup:
    final Map<String, String> actual = [
      SOME_HEADER: "my-interesting-info",
    ]

    TagContext context = codec.extract(new TextMapAdapter(actual))

    expect:
    !(context instanceof ExtractedContext)
    context.getTags() == ["some-tag": "my-interesting-info"]
  }

  def "extract empty headers returns null"() {
    expect:
    codec.extract(new TextMapAdapter(["ignored-header": "ignored-value"])) == null
  }

  def "extract http headers with larger than Java long IDs"() {
    setup:
    String largeTraceId = "9523372036854775807"
    String largeSpanId = "15815582334751494918"
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : largeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : largeSpanId,
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = codec.extract(new TextMapAdapter(actual))

    expect:
    context.getTraceId() == largeTraceId
    context.getSpanId() == largeSpanId
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with uint 64 max IDs"() {
    setup:
    String largeSpanId = BIG_INTEGER_UINT64_MAX.subtract(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : BIG_INTEGER_UINT64_MAX.toString(),
      (SPAN_ID_KEY.toUpperCase())             : BIG_INTEGER_UINT64_MAX.minus(1).toString(),
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    final ExtractedContext context = codec.extract(new TextMapAdapter(actual))

    expect:
    context.getTraceId() == BIG_INTEGER_UINT64_MAX.toString()
    context.getSpanId() == largeSpanId
    context.getBaggage().get("k1") == "v1"
    context.getBaggage().get("k2") == "v2"
    context.getTags() == ["some-tag": "my-interesting-info"]
    context.getSamplingPriority() == samplingPriority

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with invalid non-numeric ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "traceID",
      (SPAN_ID_KEY.toUpperCase())             : "spanID",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    codec.extract(new TextMapAdapter(actual))

    then:
    def iae = thrown(IllegalArgumentException)
    assert iae.cause instanceof NumberFormatException

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range trace ID"() {
    setup:
    String outOfRangeTraceId = BIG_INTEGER_UINT64_MAX.add(BigInteger.ONE).toString()
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : outOfRangeTraceId,
      (SPAN_ID_KEY.toUpperCase())             : "0",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    codec.extract(new TextMapAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }

  def "extract http headers with out of range span ID"() {
    setup:
    final Map<String, String> actual = [
      (TRACE_ID_KEY.toUpperCase())            : "0",
      (SPAN_ID_KEY.toUpperCase())             : "-1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k1"): "v1",
      (OT_BAGGAGE_PREFIX.toUpperCase() + "k2"): "v2",
      SOME_HEADER                             : "my-interesting-info",
    ]

    if (samplingPriority != PrioritySampling.UNSET) {
      actual.put(SAMPLING_PRIORITY_KEY, String.valueOf(samplingPriority))
    }

    when:
    codec.extract(new TextMapAdapter(actual))

    then:
    thrown(IllegalArgumentException)

    where:
    samplingPriority              | _
    PrioritySampling.UNSET        | _
    PrioritySampling.SAMPLER_KEEP | _
  }
}
