/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gemstone.gemfire;

import java.io.DataInput;

/**
 * An <code>InvalidDeltaException</code> is thrown when a delta cannot be
 * successfully applied by the receiving peer/client. The class implementing
 * {@link Delta} may also choose to throw this in
 * {@link Delta#fromDelta(DataInput in)}. GemFire, on encountering this
 * exception distributes the full application object.
 * 
 * @since GemFire 6.1
 */
public class InvalidDeltaException extends GemFireException {

  /**
   * Creates a new <code>InvalidDeltaException</code>. 
   */
  public InvalidDeltaException() {
  }

  /**
   * Creates a new <code>InvalidDeltaException</code>. 
   * @param msg String explaining the exception
   */
  public InvalidDeltaException(String msg) {
    super(msg);
  }

  /**
   * Creates a new <code>InvalidDeltaException</code>. 
   * @param e Throwable
   */
  public InvalidDeltaException(Throwable e) {
    super(e);
  }

  /**
   * Creates a new <code>InvalidDeltaException</code>. 
   * @param msg String explaining the exception
   * @param e Throwable
   */
  public InvalidDeltaException(String msg, Throwable e) {
    super(msg, e);
  }

}
