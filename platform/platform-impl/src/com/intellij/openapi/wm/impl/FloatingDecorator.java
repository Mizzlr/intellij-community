// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScreenUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class FloatingDecorator extends JDialog {
  private static final Logger LOG=Logger.getInstance("#com.intellij.openapi.wm.impl.FloatingDecorator");

  static final int DIVIDER_WIDTH = 3;

  private static final int ANCHOR_TOP=1;
  private static final int ANCHOR_LEFT=2;
  private static final int ANCHOR_BOTTOM=4;
  private static final int ANCHOR_RIGHT=8;

  private static final int DELAY=15; // Delay between frames
  private static final int TOTAL_FRAME_COUNT=7; // Total number of frames in animation sequence

  private final InternalDecorator myInternalDecorator;
  private final MyUISettingsListener myUISettingsListener;
  private WindowInfoImpl myInfo;

  private final Disposable myDisposable = Disposer.newDisposable();
  private final Alarm myDelayAlarm; // Determines moment when tool window should become transparent
  private final Alarm myFrameTicker; // Determines moments of rendering of next frame
  private final MyAnimator myAnimator; // Renders alpha ratio
  private int myCurrentFrame; // current frame in transparency animation
  private float myStartRatio;
  private float myEndRatio; // start and end alpha ratio for transparency animation


  FloatingDecorator(final IdeFrameImpl owner, @NotNull WindowInfoImpl info, @NotNull InternalDecorator internalDecorator){
    super(owner,internalDecorator.getToolWindow().getId());
    MnemonicHelper.init(getContentPane());
    myInternalDecorator=internalDecorator;

    setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    final JComponent cp=(JComponent)getContentPane();
    cp.setLayout(new BorderLayout());

    if(SystemInfo.isWindows){
      setUndecorated(true);
      cp.add(new BorderItem(ANCHOR_TOP),BorderLayout.NORTH);
      cp.add(new BorderItem(ANCHOR_LEFT),BorderLayout.WEST);
      cp.add(new BorderItem(ANCHOR_BOTTOM),BorderLayout.SOUTH);
      cp.add(new BorderItem(ANCHOR_RIGHT),BorderLayout.EAST);
      cp.add(myInternalDecorator,BorderLayout.CENTER);
    }else{
      // Due to JDK's bug #4234645 we cannot support custom decoration on Linux platform.
      // The prblem is that Window.setLocation() doesn't work properly wjen the dialod is displayable.
      // Therefore we use native WM decoration.
      // TODO[vova] investigate the problem under Mac OSX.
      cp.add(myInternalDecorator,BorderLayout.CENTER);
      getRootPane().putClientProperty("Window.style", "small");
    }

    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    addWindowListener(new MyWindowListener());

    //

    myDelayAlarm=new Alarm();
    myFrameTicker=new Alarm(Alarm.ThreadToUse.POOLED_THREAD,myDisposable);
    myAnimator=new MyAnimator();
    myCurrentFrame=0;
    myStartRatio=0.0f;
    myEndRatio=0.0f;

    myUISettingsListener=new MyUISettingsListener();

    //

    IdeGlassPaneImpl ideGlassPane = new IdeGlassPaneImpl(getRootPane(), true);
    getRootPane().setGlassPane(ideGlassPane);

    //workaround: we need to add this IdeGlassPane instance as dispatcher in IdeEventQueue
    ideGlassPane.addMousePreprocessor(new MouseAdapter() {
    }, myDisposable);

    apply(info);
  }

  @Override
  public final void show(){
    setFocusableWindowState(myInfo.isActive());

    super.show();
    final UISettings uiSettings = UISettings.getInstance();
    if (uiSettings.getState().getEnableAlphaMode()) {
      final WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      windowManager.setAlphaModeEnabled(this, true);
      if (myInfo.isActive()) {
        windowManager.setAlphaModeRatio(this, 0.0f);
      }
      else {
        windowManager.setAlphaModeRatio(this, uiSettings.getState().getAlphaModeRatio());
      }
    }
    paint(getGraphics()); // This prevents annoying flick

    setFocusableWindowState(true);

    ApplicationManager.getApplication().getMessageBus().connect(myDelayAlarm).subscribe(UISettingsListener.TOPIC, myUISettingsListener);
  }

  @Override
  public final void dispose(){
    if (ScreenUtil.isStandardAddRemoveNotify(getParent())) {
      Disposer.dispose(myDelayAlarm);
      Disposer.dispose(myDisposable);
    } else {
      if (isShowing()) {
        SwingUtilities.invokeLater(() -> show());
      }
    }
    super.dispose();
  }

  final void apply(@NotNull WindowInfoImpl info){
    LOG.assertTrue(info.isFloating());
    myInfo=info;
    // Set alpha mode
    final UISettings uiSettings=UISettings.getInstance();
    if (uiSettings.getState().getEnableAlphaMode() && isShowing() && isDisplayable()) {
      myDelayAlarm.cancelAllRequests();
      if (myInfo.isActive()) { // make window non transparent
        myFrameTicker.cancelAllRequests();
        myStartRatio = getCurrentAlphaRatio();
        if (myCurrentFrame > 0) {
          myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
        }
        myEndRatio = .0f;
        myFrameTicker.addRequest(myAnimator, DELAY);
      }
      else { // make window transparent
        myDelayAlarm.addRequest(
          () -> {
            myFrameTicker.cancelAllRequests();
            myStartRatio = getCurrentAlphaRatio();
            if (myCurrentFrame > 0) {
              myCurrentFrame = TOTAL_FRAME_COUNT - myCurrentFrame;
            }
            myEndRatio = uiSettings.getState().getAlphaModeRatio();
            myFrameTicker.addRequest(myAnimator, DELAY);
          },
          uiSettings.getState().getAlphaModeDelay()
        );
      }
    }
  }

  private float getCurrentAlphaRatio(){
    float delta=(myEndRatio-myStartRatio)/(float)TOTAL_FRAME_COUNT;
    if(myStartRatio>myEndRatio){ // dialog is becoming non transparent quicker
      delta*=2;
    }
    final float ratio=myStartRatio+(float)myCurrentFrame*delta;
    return Math.min(1.0f,Math.max(.0f,ratio));
  }

  private final class BorderItem extends JPanel{
    private static final int RESIZER_WIDTH=10;

    private final int myAnchor;
    private int myMotionMask;
    private Point myLastPoint;
    private boolean myDragging;

    BorderItem(final int anchor){
      myAnchor=anchor;
      enableEvents(MouseEvent.MOUSE_EVENT_MASK|MouseEvent.MOUSE_MOTION_EVENT_MASK);
    }

    @Override
    protected final void processMouseMotionEvent(final MouseEvent e){
      super.processMouseMotionEvent(e);
      if(MouseEvent.MOUSE_DRAGGED==e.getID() && myLastPoint != null){
        final Point newPoint=e.getPoint();
        SwingUtilities.convertPointToScreen(newPoint,this);
        final Rectangle screenBounds=WindowManagerEx.getInstanceEx().getScreenBounds();
        int screenMaxX = screenBounds.x + screenBounds.width;
        int screenMaxY = screenBounds.y + screenBounds.height;

        newPoint.x = Math.min(Math.max(newPoint.x, screenBounds.x), screenMaxX);
        newPoint.y = Math.min(Math.max(newPoint.y, screenBounds.y), screenMaxY);

        final Rectangle oldBounds=FloatingDecorator.this.getBounds();
        final Rectangle newBounds=new Rectangle(oldBounds);

        if((myMotionMask&ANCHOR_TOP)>0){
          newPoint.y=Math.min(newPoint.y,oldBounds.y+oldBounds.height-2*DIVIDER_WIDTH);
          if(newPoint.y<screenBounds.y+DIVIDER_WIDTH){
            newPoint.y=screenBounds.y;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.y=oldBounds.y+offset.y;
          newBounds.height=oldBounds.height-offset.y;
        }
        if((myMotionMask&ANCHOR_LEFT)>0){
          newPoint.x=Math.min(newPoint.x,oldBounds.x+oldBounds.width-2*DIVIDER_WIDTH);
          if(newPoint.x<screenBounds.x+DIVIDER_WIDTH){
            newPoint.x=screenBounds.x;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.x=oldBounds.x+offset.x;
          newBounds.width=oldBounds.width-offset.x;
        }
        if((myMotionMask&ANCHOR_BOTTOM)>0){
          newPoint.y=Math.max(newPoint.y,oldBounds.y+2*DIVIDER_WIDTH);
          if (newPoint.y > screenMaxY - DIVIDER_WIDTH) {
            newPoint.y = screenMaxY;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.height=oldBounds.height+offset.y;
        }
        if((myMotionMask&ANCHOR_RIGHT)>0){
          newPoint.x=Math.max(newPoint.x,oldBounds.x+2*DIVIDER_WIDTH);
          if (newPoint.x > screenMaxX - DIVIDER_WIDTH) {
            newPoint.x = screenMaxX;
          }
          final Point offset=new Point(newPoint.x-myLastPoint.x,newPoint.y-myLastPoint.y);
          newBounds.width=oldBounds.width+offset.x;
        }
        // It's much better to resize frame this way then via Component.setBounds() method.
        // Component.setBounds() method cause annoying repainting and blinking.
        //FloatingDecorator.this.getPeer().setBounds(newBounds.x,newBounds.y,newBounds.width,newBounds.height, 0);
        FloatingDecorator.this.setBounds(newBounds.x,newBounds.y,newBounds.width,newBounds.height);

        myLastPoint=newPoint;
      } else if(e.getID()==MouseEvent.MOUSE_MOVED){
        if(!myDragging){
          setMotionMask(e.getPoint());
        }
      }
    }

    @Override
    protected final void processMouseEvent(final MouseEvent e){
      super.processMouseEvent(e);
      switch(e.getID()){
        case MouseEvent.MOUSE_PRESSED:{
          myLastPoint=e.getPoint();
          SwingUtilities.convertPointToScreen(myLastPoint,this);
          setMotionMask(e.getPoint());
          myDragging=true;
          break;
        }case MouseEvent.MOUSE_RELEASED:{
          FloatingDecorator.this.validate();
          FloatingDecorator.this.repaint();
          myDragging=false;
          break;
        }case MouseEvent.MOUSE_ENTERED:{
          if(!myDragging){
            setMotionMask(e.getPoint());
          }
        }
      }
    }

    private void setMotionMask(final Point p){
      myMotionMask=myAnchor;
      if(ANCHOR_TOP==myAnchor||ANCHOR_BOTTOM==myAnchor){
        if(p.getX()<RESIZER_WIDTH){
          myMotionMask|=ANCHOR_LEFT;
        } else if(p.getX()>getWidth()-RESIZER_WIDTH){
          myMotionMask|=ANCHOR_RIGHT;
        }
      } else{
        if(p.getY()<RESIZER_WIDTH){
          myMotionMask|=ANCHOR_TOP;
        } else if(p.getY()>getHeight()-RESIZER_WIDTH){
          myMotionMask|=ANCHOR_BOTTOM;
        }
      }
      if(myMotionMask==ANCHOR_TOP){
        setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_TOP|ANCHOR_LEFT)){
        setCursor(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_LEFT){
        setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_LEFT|ANCHOR_BOTTOM)){
        setCursor(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_BOTTOM){
        setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_BOTTOM|ANCHOR_RIGHT)){
        setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
      } else if(myMotionMask==ANCHOR_RIGHT){
        setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
      } else if(myMotionMask==(ANCHOR_RIGHT|ANCHOR_TOP)){
        setCursor(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR));
      }
    }

    @Override
    public final Dimension getPreferredSize(){
      final Dimension d=super.getPreferredSize();
      if(ANCHOR_TOP==myAnchor||ANCHOR_BOTTOM==myAnchor){
        d.height=DIVIDER_WIDTH;
      } else{
        d.width=DIVIDER_WIDTH;
      }
      return d;
    }

    @Override
    public final void paint(final Graphics g){
      super.paint(g);
      final JBColor lightGray = new JBColor(Color.lightGray, Gray._95);
      final JBColor gray = new JBColor(Color.gray, Gray._95);
      if(ANCHOR_TOP==myAnchor){
        g.setColor(lightGray);
        UIUtil.drawLine(g, 0, 0, getWidth() - 1, 0);
        UIUtil.drawLine(g, 0, 0, 0, getHeight() - 1);
        g.setColor(JBColor.GRAY);
        UIUtil.drawLine(g, getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
      } else if(ANCHOR_LEFT==myAnchor){
        g.setColor(lightGray);
        UIUtil.drawLine(g, 0, 0, 0, getHeight() - 1);
      } else {
        if(ANCHOR_BOTTOM==myAnchor){
          g.setColor(lightGray);
          UIUtil.drawLine(g, 0, 0, 0, getHeight() - 1);
          g.setColor(gray);
          UIUtil.drawLine(g, 0, getHeight() - 1, getWidth() - 1, getHeight() - 1);
          UIUtil.drawLine(g, getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
        } else{ // RIGHT
          g.setColor(gray);
          UIUtil.drawLine(g, getWidth() - 1, 0, getWidth() - 1, getHeight() - 1);
        }
      }
    }
  }

  private final class MyWindowListener extends WindowAdapter{
    @Override
    public void windowClosing(final WindowEvent e){
      myInternalDecorator.fireResized();
      myInternalDecorator.fireHidden();
    }
  }

  private final class MyAnimator implements Runnable{
    @Override
    public final void run(){
      final WindowManagerEx windowManager=WindowManagerEx.getInstanceEx();
      if(isDisplayable()&&isShowing()){
        windowManager.setAlphaModeRatio(FloatingDecorator.this,getCurrentAlphaRatio());
      }
      if(myCurrentFrame<TOTAL_FRAME_COUNT){
        myCurrentFrame++;
        myFrameTicker.addRequest(myAnimator,DELAY);
      }
      else {
        myFrameTicker.cancelAllRequests();
      }
    }
  }

  private final class MyUISettingsListener implements UISettingsListener {
    @Override
    public void uiSettingsChanged(final UISettings uiSettings) {
      LOG.assertTrue(isDisplayable());
      LOG.assertTrue(isShowing());
      final WindowManagerEx windowManager = WindowManagerEx.getInstanceEx();
      myDelayAlarm.cancelAllRequests();
      if (uiSettings.getState().getEnableAlphaMode()) {
        if (!myInfo.isActive()) {
          windowManager.setAlphaModeEnabled(FloatingDecorator.this, true);
          windowManager.setAlphaModeRatio(FloatingDecorator.this, uiSettings.getState().getAlphaModeRatio());
        }
      }
      else {
        windowManager.setAlphaModeEnabled(FloatingDecorator.this, false);
      }
    }
  }
}