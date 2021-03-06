// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class TabHeaderComponent extends JComponent {
  private final List<Computable<String>> myTabs = new ArrayList<>();
  private final JComponent myToolbarComponent;
  private final TabHeaderListener myListener;
  private int mySelectionTab = -1;
  private int myHoverTab = -1;
  private SizeInfo mySizeInfo;
  private int myBaselineY;
  private Breadcrumbs myBreadcrumbs;

  public TabHeaderComponent(@NotNull DefaultActionGroup actions, @NotNull TabHeaderListener listener) {
    myListener = listener;
    add(myToolbarComponent = createToolbar(actions));
    setBackground(JBUI.CurrentTheme.ToolWindow.headerBackground());
    setOpaque(true);

    MouseAdapter mouseHandler = new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent event) {
        if (SwingUtilities.isLeftMouseButton(event)) {
          int tab = findTab(event);
          if (tab != -1 && tab != mySelectionTab) {
            setSelectionWithEvents(tab);
          }
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        if (myHoverTab != -1) {
          myHoverTab = -1;
          repaint();
        }
      }

      @Override
      public void mouseMoved(MouseEvent event) {
        int tab = findTab(event);
        if (tab != -1 && tab != myHoverTab) {
          myHoverTab = tab;
          repaint();
        }
      }
    };
    addMouseListener(mouseHandler);
    addMouseMotionListener(mouseHandler);
  }

  @Override
  public void addNotify() {
    super.addNotify();

    addTabSelectionAction(IdeActions.ACTION_NEXT_TAB,
                          () -> setSelectionWithEvents(mySelectionTab == myTabs.size() - 1 ? 0 : mySelectionTab + 1));

    addTabSelectionAction(IdeActions.ACTION_PREVIOUS_TAB,
                          () -> setSelectionWithEvents(mySelectionTab == 0 ? myTabs.size() - 1 : mySelectionTab - 1));
  }

  private void addTabSelectionAction(@NotNull String actionId, @NotNull Runnable callback) {
    AnAction action = ActionManager.getInstance().getAction(actionId);
    if (action == null) {
      return;
    }

    AnAction localAction = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        if (isShowing() && !myTabs.isEmpty()) {
          callback.run();
        }
      }
    };
    localAction.copyShortcutFrom(action);
    localAction.registerCustomShortcutSet(getRootPane(), null);
  }

  @NotNull
  private static JComponent createToolbar(@NotNull DefaultActionGroup actions) {
    DefaultActionGroup toolbarActionGroup = new DefaultActionGroup();
    ActionToolbar toolbar =
      ActionManager.getInstance().createActionToolbar(ActionPlaces.NAVIGATION_BAR_TOOLBAR, toolbarActionGroup, true);
    toolbar.setReservePlaceAutoPopupIcon(false);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    JComponent toolbarComponent = toolbar.getComponent();
    toolbarActionGroup.add(new DumbAwareAction(null, null, AllIcons.General.GearPlain) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ListPopup actionGroupPopup = JBPopupFactory.getInstance().
          createActionGroupPopup(null, actions, e.getDataContext(), true, null, Integer.MAX_VALUE);

        HelpTooltip.setMasterPopup(e.getInputEvent().getComponent(), actionGroupPopup);
        actionGroupPopup.show(new RelativePoint(toolbarComponent, getPopupPoint()));
      }

      private Point getPopupPoint() {
        int dH = UIUtil.isUnderWin10LookAndFeel() ? JBUI.scale(1) : 0;
        return new Point(JBUI.scale(2), toolbarComponent.getHeight() - dH);
      }
    });
    toolbarComponent.setBorder(JBUI.Borders.empty());
    return toolbarComponent;
  }

  public void addTab(@NotNull String title) {
    addTab(() -> title);
  }

  public void addTab(@NotNull Computable<String> titleComputable) {
    myTabs.add(titleComputable);
    update();
  }

  public void update() {
    mySizeInfo = null;
    revalidate();
    repaint();
  }

  public int getSelectionTab() {
    return mySelectionTab;
  }

  public void clearSelection() {
    setSelection(-1);
  }

  public void setSelection(int index) {
    if (index < 0) {
      mySelectionTab = -1;
    }
    else if (index >= myTabs.size()) {
      mySelectionTab = myTabs.size() - 1;
    }
    else {
      mySelectionTab = index;
    }
    repaint();
  }

  public void setSelectionWithEvents(int index) {
    mySelectionTab = index;
    myListener.selectionChanged(index);
    repaint();
  }

  @TestOnly
  @NotNull
  public Point getTabLocation(@NotNull final String tabTitle) {
    calculateSize();
    for (int i = 0; i < myTabs.size(); ++i) {
      if (myTabs.get(i).compute().equals(tabTitle)) {
        final Point point = mySizeInfo.tabs[i].getLocation();
        return new Point(getStartX() + point.x, point.y);
      }
    }
    throw new IllegalArgumentException("Tab " + tabTitle + " not found");
  }

  private int findTab(@NotNull MouseEvent event) {
    calculateSize();
    int x = getStartX();
    int height = getHeight();
    int eventX = event.getX();
    int eventY = event.getY();

    for (int i = 0, size = myTabs.size(); i < size; i++) {
      Rectangle bounds = mySizeInfo.tabs[i];
      if (new Rectangle(x + bounds.x, 0, bounds.width, height).contains(eventX, eventY)) {
        return i;
      }
    }

    return -1;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    if (g instanceof Graphics2D) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    calculateSize();

    FontMetrics fm = getFontMetrics(getFont());
    int x = getStartX();
    int height = getHeight();
    int tabTitleY = fm.getAscent() + (height - fm.getHeight()) / 2;
    if (myBreadcrumbs != null) {
      tabTitleY = myBaselineY + myBreadcrumbs.getBaseline();
    }

    for (int i = 0, size = myTabs.size(); i < size; i++) {
      if (mySelectionTab == i || myHoverTab == i) {
        Rectangle bounds = mySizeInfo.tabs[i];
        g.setColor(mySelectionTab == i
                   ? JBUI.CurrentTheme.ToolWindow.tabSelectedBackground()
                   : JBUI.CurrentTheme.ToolWindow.tabHoveredBackground());
        g.fillRect(x + bounds.x, 0, bounds.width, height);
        g.setColor(getForeground());
      }

      g.drawString(myTabs.get(i).compute(), x + mySizeInfo.tabTitleX[i], tabTitleY);
    }
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    myBaselineY = y;
    super.setBounds(x, 0, width, height += y);

    if (myBreadcrumbs == null) {
      myBreadcrumbs = UIUtil.findComponentOfType((JComponent)getParent(), Breadcrumbs.class);
    }

    calculateSize();

    Dimension size = myToolbarComponent.getPreferredSize();
    int toolbarX = getStartX() + mySizeInfo.toolbarX;
    int toolbarY = (height - size.height) / 2;
    myToolbarComponent.setBounds(toolbarX, toolbarY, size.width, size.height);
  }

  private int getStartX() {
    return (getParent().getWidth() - mySizeInfo.width) / 2 - getX();
  }

  @Override
  public Dimension getPreferredSize() {
    calculateSize();
    return new Dimension(mySizeInfo.width, JBUI.scale(30));
  }

  private void calculateSize() {
    if (mySizeInfo != null) {
      return;
    }

    mySizeInfo = new SizeInfo();

    int size = myTabs.size();
    mySizeInfo.tabs = new Rectangle[size];
    mySizeInfo.tabTitleX = new int[size];

    int offset = JBUI.scale(22);
    int x = 0;
    FontMetrics fm = getFontMetrics(getFont());

    for (int i = 0; i < size; i++) {
      int tabWidth = offset + UIUtilities.stringWidth(null, fm, myTabs.get(i).compute()) + offset;
      mySizeInfo.tabTitleX[i] = x + offset;
      mySizeInfo.tabs[i] = new Rectangle(x, 0, tabWidth, -1);
      x += tabWidth;
    }

    Dimension toolbarSize = myToolbarComponent.getPreferredSize();
    x += JBUI.scale(10);
    mySizeInfo.width = x + toolbarSize.width;
    mySizeInfo.toolbarX = x;
  }

  private static class SizeInfo {
    public int width;

    public Rectangle[] tabs;
    public int[] tabTitleX;

    public int toolbarX;
  }
}
