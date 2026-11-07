/*
  ===============================================================================

   This file is part of the JUCE framework.
   Copyright (c) Raw Material Software Limited

   JUCE is an open source framework subject to commercial or open source
   licensing. The JUCE End User Licence Agreement and incorporated terms apply.

   Or:

   You may also use this code under the terms of the AGPLv3:
   https://www.gnu.org/licenses/agpl-3.0.en.html

   THE JUCE FRAMEWORK IS PROVIDED "AS IS" WITHOUT ANY WARRANTY.

  ===============================================================================
*/
package com.rmsl.juce;

import android.content.Context;

public class Java {
    static {
        System.loadLibrary("radiant");
    }

    public static native void initialiseJUCE(Context appContext);
}
