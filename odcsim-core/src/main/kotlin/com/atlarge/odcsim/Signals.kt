/*
 * MIT License
 *
 * Copyright (c) 2018 atlarge-research
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

package com.atlarge.odcsim

/**
 * System signals are notifications that are generated by the system and delivered to the actor behavior in a reliable
 * fashion.
 */
interface Signal

/**
 * Lifecycle signal that is fired upon creation of the actor. This will be the first message that the actor receives.
 */
object PreStart : Signal

/**
 * Lifecycle signal that is fired after this actor and all its child actors (transitively) have terminated.
 */
object PostStop : Signal

/**
 * A [Signal] to indicate an actor has timed out.
 *
 * This class contains a [target] property in order to allow nested behavior to function properly when multiple layers
 * are waiting on this signal.
 *
 * @property target The target object that has timed out.
 */
class Timeout(val target: Any) : Signal
