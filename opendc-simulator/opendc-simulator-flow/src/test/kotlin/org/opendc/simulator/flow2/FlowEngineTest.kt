/*
 * Copyright (c) 2022 AtLarge Research
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.opendc.simulator.flow2

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.opendc.simulator.flow2.mux.MaxMinFlowMultiplexer
import org.opendc.simulator.flow2.sink.SimpleFlowSink
import org.opendc.simulator.flow2.source.SimpleFlowSource
import org.opendc.simulator.kotlin.runSimulation

/**
 * Smoke tests for the Flow API.
 */
class FlowEngineTest {
    @Test
    fun testSmoke() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val multiplexer = MaxMinFlowMultiplexer(graph)
            val sink = SimpleFlowSink(graph, 2.0f)

            graph.connect(multiplexer.newOutPort(), sink.input)

            val sourceA = SimpleFlowSource(graph, 2000.0f, 0.8f)
            val sourceB = SimpleFlowSource(graph, 2000.0f, 0.8f)

            graph.connect(sourceA.output, multiplexer.newInput())
            graph.connect(sourceB.output, multiplexer.newInput())
        }

    @Test
    fun testConnectInvalidInPort() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val InPort = mockk<InPort>()
            val source = SimpleFlowSource(graph, 2000.0f, 0.8f)
            assertThrows<IllegalArgumentException> { graph.connect(source.output, InPort) }
        }

    @Test
    fun testConnectInvalidOutPort() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val OutPort = mockk<OutPort>()
            val sink = SimpleFlowSink(graph, 2.0f)
            assertThrows<IllegalArgumentException> { graph.connect(OutPort, sink.input) }
        }

    @Test
    fun testConnectInPortBelongsToDifferentGraph() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graphA = engine.newGraph()
            val graphB = engine.newGraph()

            val sink = SimpleFlowSink(graphB, 2.0f)
            val source = SimpleFlowSource(graphA, 2000.0f, 0.8f)

            assertThrows<IllegalArgumentException> { graphA.connect(source.output, sink.input) }
        }

    @Test
    fun testConnectOutPortBelongsToDifferentGraph() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graphA = engine.newGraph()
            val graphB = engine.newGraph()

            val sink = SimpleFlowSink(graphA, 2.0f)
            val source = SimpleFlowSource(graphB, 2000.0f, 0.8f)

            assertThrows<IllegalArgumentException> { graphA.connect(source.output, sink.input) }
        }

    @Test
    fun testConnectInPortAlreadyConnected() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val sink = SimpleFlowSink(graph, 2.0f)
            val sourceA = SimpleFlowSource(graph, 2000.0f, 0.8f)
            val sourceB = SimpleFlowSource(graph, 2000.0f, 0.8f)

            graph.connect(sourceA.output, sink.input)
            assertThrows<IllegalStateException> { graph.connect(sourceB.output, sink.input) }
        }

    @Test
    fun testConnectOutPortAlreadyConnected() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val sinkA = SimpleFlowSink(graph, 2.0f)
            val sinkB = SimpleFlowSink(graph, 2.0f)
            val source = SimpleFlowSource(graph, 2000.0f, 0.8f)

            graph.connect(source.output, sinkA.input)
            assertThrows<IllegalStateException> { graph.connect(source.output, sinkB.input) }
        }

    @Test
    fun testDisconnectInPortInvalid() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val InPort = mockk<InPort>()
            assertThrows<IllegalArgumentException> { graph.disconnect(InPort) }
        }

    @Test
    fun testDisconnectOutPortInvalid() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val OutPort = mockk<OutPort>()
            assertThrows<IllegalArgumentException> { graph.disconnect(OutPort) }
        }

    @Test
    fun testDisconnectInPortInvalidGraph() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graphA = engine.newGraph()
            val graphB = engine.newGraph()

            val sink = SimpleFlowSink(graphA, 2.0f)

            assertThrows<IllegalArgumentException> { graphB.disconnect(sink.input) }
        }

    @Test
    fun testDisconnectOutPortInvalidGraph() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graphA = engine.newGraph()
            val graphB = engine.newGraph()

            val source = SimpleFlowSource(graphA, 2000.0f, 0.8f)

            assertThrows<IllegalArgumentException> { graphB.disconnect(source.output) }
        }

    @Test
    fun testInPortEquality() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val sinkA = SimpleFlowSink(graph, 2.0f)
            val sinkB = SimpleFlowSink(graph, 2.0f)

            val multiplexer = MaxMinFlowMultiplexer(graph)

            assertEquals(sinkA.input, sinkA.input)
            assertNotEquals(sinkA.input, sinkB.input)

            assertNotEquals(multiplexer.newInput(), multiplexer.newInput())
        }

    @Test
    fun testOutPortEquality() =
        runSimulation {
            val engine = FlowEngine.create(dispatcher)
            val graph = engine.newGraph()

            val sourceA = SimpleFlowSource(graph, 2000.0f, 0.8f)
            val sourceB = SimpleFlowSource(graph, 2000.0f, 0.8f)

            val multiplexer = MaxMinFlowMultiplexer(graph)

            assertEquals(sourceA.output, sourceA.output)
            assertNotEquals(sourceA.output, sourceB.output)

            assertNotEquals(multiplexer.newOutPort(), multiplexer.newOutPort())
        }
}
