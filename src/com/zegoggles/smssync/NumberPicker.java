/*
 * Copyright (c) 2010, Jeffrey F. Cole
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *
 *  Neither the name of the technologichron.net nor the names of its contributors
 *  may be used to endorse or promote products derived from this software
 *  without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.zegoggles.smssync;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * A simple layout group that provides a numeric text area with two buttons to
 * increment or decrement the value in the text area. Holding either button
 * will auto increment the value up or down appropriately.
 *
 * @author Jeffrey F. Cole
 *
 */
public class NumberPicker extends LinearLayout {
  private final long mRepeatDelay;
  private final int mTextSize;

  private final float mMinimum;
  private final float mMaximum;
  private final float mIncrement;

  private Float value;

  Button decrement;
  Button increment;
  public EditText valueText;

  private Handler repeatUpdateHandler = new Handler();

  private boolean autoIncrement = false;
  private boolean autoDecrement = false;

  /**
   * This little guy handles the auto part of the auto incrementing feature.
   * In doing so it instantiates itself. There has to be a pattern name for
   * that...
   *
   * @author Jeffrey F. Cole
   *
   */
  class RepetetiveUpdater implements Runnable {
    public void run() {
      if( autoIncrement ){
        increment();
        repeatUpdateHandler.postDelayed( new RepetetiveUpdater(), mRepeatDelay );
      } else if( autoDecrement ){
        decrement();
        repeatUpdateHandler.postDelayed( new RepetetiveUpdater(), mRepeatDelay );
      }
    }
  }

  public NumberPicker( Context context, AttributeSet attrs ) {
    super(context, attrs);

    LayoutParams elementParams = new LinearLayout.LayoutParams( LayoutParams.WRAP_CONTENT,
                                                                LayoutParams.WRAP_CONTENT );
    this.setLayoutParams( elementParams );

    final String NS = "http://zegoggles.com/smssync";
    mMinimum = attrs.getAttributeFloatValue(NS, "minimumValue", 0f);
    mMaximum = attrs.getAttributeFloatValue(NS, "maximumValue", 999f);
    mIncrement = attrs.getAttributeFloatValue(NS, "increment", 1.0f);
    final float initialValue = attrs.getAttributeFloatValue(NS, "initialValue", 0f);

    mTextSize = attrs.getAttributeIntValue(NS, "textSize", 18);
    mRepeatDelay = attrs.getAttributeIntValue(NS, "repeatDelay", 50);


    // init the individual elements
    initDecrementButton( context );
    initValueEditText( context, initialValue );
    initIncrementButton( context );

    // Can be configured to be vertical or horizontal
    // Thanks for the help, LinearLayout!
    if( getOrientation() == VERTICAL ){
      addView( increment, elementParams );
      addView( valueText, elementParams );
      addView( decrement, elementParams );
    } else {
      addView( decrement, elementParams );
      addView( valueText, elementParams );
      addView( increment, elementParams );
    }
  }

  private Typeface getButtonTypeface() {
    return Typeface.MONOSPACE;
  }

  private void initIncrementButton( Context context){
    increment = new Button( context );
    increment.setTextSize( mTextSize );
    increment.setText( " + " );
    increment.setTypeface(getButtonTypeface());

    // Increment once for a click
    increment.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              increment();
            }
        });

    // Auto increment for a long click
    increment.setOnLongClickListener(
        new View.OnLongClickListener(){
          public boolean onLongClick(View arg0) {
            autoIncrement = true;
            repeatUpdateHandler.post( new RepetetiveUpdater() );
            return false;
          }
        }
    );

    // When the button is released, if we're auto incrementing, stop
    increment.setOnTouchListener( new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        if( event.getAction() == MotionEvent.ACTION_UP && autoIncrement ){
          autoIncrement = false;
        }
        return false;
      }
    });
  }

  private void initValueEditText( Context context, float initialValue){

    value = Float.valueOf( initialValue );

    valueText = new EditText( context );
    valueText.setTextSize(mTextSize);

    // Since we're a number that gets affected by the button, we need to be
    // ready to change the numeric value with a simple ++/--, so whenever
    // the value is changed with a keyboard, convert that text value to a
    // number. We can set the text area to only allow numeric input, but
    // even so, a carriage return can get hacked through. To prevent this
    // little quirk from causing a crash, store the value of the internal
    // number before attempting to parse the changed value in the text area
    // so we can revert to that in case the text change causes an invalid
    // number
    valueText.setOnKeyListener(new View.OnKeyListener() {
      public boolean onKey(View v, int arg1, KeyEvent event) {
        float backupValue = value;
        try {
          value = Float.parseFloat( ((EditText)v).getText().toString() );
        } catch( NumberFormatException nfe ){
          value = backupValue;
        }
        return false;
      }
    });

    // Highlight the number when we get focus
    valueText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
      public void onFocusChange(View v, boolean hasFocus) {
        if( hasFocus ){
          //((EditText)v).selectAll();
        }
      }
    });

    valueText.setGravity( Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL );
    valueText.setText( value.toString() );
    valueText.setInputType( InputType.TYPE_CLASS_NUMBER );
  }

  private void initDecrementButton( Context context){
    decrement = new Button( context );
    decrement.setTextSize( mTextSize );
    decrement.setText( " - " );
    decrement.setTypeface(getButtonTypeface());


    // Decrement once for a click
    decrement.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              decrement();
            }
        });


    // Auto Decrement for a long click
    decrement.setOnLongClickListener(
        new View.OnLongClickListener(){
          public boolean onLongClick(View arg0) {
            autoDecrement = true;
            repeatUpdateHandler.post( new RepetetiveUpdater() );
            return false;
          }
        }
    );

    // When the button is released, if we're auto decrementing, stop
    decrement.setOnTouchListener( new View.OnTouchListener() {
      public boolean onTouch(View v, MotionEvent event) {
        if( event.getAction() == MotionEvent.ACTION_UP && autoDecrement ){
          autoDecrement = false;
        }
        return false;
      }
    });
  }

  public void increment(){
    if( value < mMaximum ){
      value = value + mIncrement;
      valueText.setText( value.toString() );
    }
  }

  public void decrement(){
    if( value > mMinimum ){
      value = value - mIncrement;
      valueText.setText( value.toString() );
    }
  }

  public float getValue(){
    return value;
  }

  public void setValue( float value ){
    if( value > mMaximum ) value = mMaximum;
    if( value >= 0 ){
      this.value = value;
      valueText.setText( this.value.toString() );
    }
  }
}
