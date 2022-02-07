# com.ericsson.research.transport.ws

## WSDataListener

Based off of  [WSDataListener](https://github.com/EricssonResearch/trap/blob/7b9a97caa711a9b63eb0352d94bb6c4a9cde3101/trap-network/trap-network-websockets-nio/src/test/java/com/ericsson/research/transport/ws/WSDataListener.java)
class. This reference implementation is coarse-grained:
it only uses one lock and one condition.

It basically has four objects (`open`, `pongs`, `strings`, `datas`) which
can be put to or taken from. This is a test of an unmaintained library.

### Contents

* `WSDataListenerInterface`: An interface to enable easy comparison
  between our implementations of WSDataListener and the reference
  implementation.
* `WSDataListener`: The original WSDataListener with 2 modifications
    - Make it implement `WSDataListenerInterface` instead of `WSListener`
    - Make sure `signalPong` is `synchronized` to avoid deadlock
* `CortadoExplicitCoarseGrainedWSDataListener`: An alternative implementation
  of AsyncDispatch as an explicit monitor with coarse-grained locking but multiple
  conditions.
* `CortadoExplicitCoarseGrainedWSDataListener`: An alternative implementation
  of AsyncDispatch as an explicit monitor with fine-grained locking and multiple
  conditions.