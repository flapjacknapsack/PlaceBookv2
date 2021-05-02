/*
 * Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.placebook.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment

//defines an interface that must be implemented by the parent Activity
class PhotoOptionDialogFragment : DialogFragment() {

  interface PhotoOptionDialogListener {
    fun onCaptureClick()
    fun onPickClick()
  }

  //A property is defined to hold an instance of PhotoOptionDialogListener
  private lateinit var listener: PhotoOptionDialogListener

  // standard onCreateDialog method for a DialogFragment
  override fun onCreateDialog(savedInstanceState: Bundle?):
      Dialog {

    // listener property is set to the parent Activity
    listener = activity as PhotoOptionDialogListener

    //The two possible option indices are initialized to -1.
    var captureSelectIdx = -1
    var pickSelectIdx = -1

    //ArrayList is defined to hold the AlertDialog options
    val options = ArrayList<String>()

    //Context object. use the activity property of the
    //AlertDialog() class as the context
    val context = activity as Context

    //If the device has a camera capable of capturing images, then a Camera option is
    //added to the options array.
    if (canCapture(context)) {
      options.add("Camera")
      captureSelectIdx = 0
    }

    //If the device can pick an image from a gallery, then a Gallery option is added
    if (canPick(context)) {
      options.add("Gallery")
      pickSelectIdx = if (captureSelectIdx == 0) 1 else 0
    }

    //The AlertDialog is built using the options list, and an onClickListener is provided
    //to respond to the user selection
    return AlertDialog.Builder(context)
        .setTitle("Photo Option")
        .setItems(options.toTypedArray<CharSequence>()) {
          _, which ->
          if (which == captureSelectIdx) {

            //If the Camera option was selected, then onCaptureClick() is called
            listener.onCaptureClick()
          } else if (which == pickSelectIdx) {

            //If the Gallery option was selected, then onPickClick() is called
            listener.onPickClick()
          }
        }
        .setNegativeButton("Cancel", null)
        .create()
  }
  companion object {

    //canPick() determines if the device can pick an image from a gallery.
    fun canPick(context: Context) : Boolean {
      val pickIntent = Intent(Intent.ACTION_PICK,
          MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
      return (pickIntent.resolveActivity(
          context.packageManager) != null)
    }

    //canCapture() determines if the device has a camera to capture a new image
    fun canCapture(context: Context) : Boolean {
      val captureIntent = Intent(
          MediaStore.ACTION_IMAGE_CAPTURE)
      return (captureIntent.resolveActivity(
          context.packageManager) != null)
    }

    //newInstance is a helper method intended to be used by the parent activity when
    //creating a new PhotoOptionDialogFragment.
    fun newInstance(context: Context):
        PhotoOptionDialogFragment? {

      //If the device can pick from a gallery or snap a new image, then the
      //PhotoOptionDialogFragment is created and returned, otherwise null is returned
      if (canPick(context) || canCapture(context)) {
        val frag = PhotoOptionDialogFragment()
        return frag
      } else {
        return null
      }
    }
  }
}
