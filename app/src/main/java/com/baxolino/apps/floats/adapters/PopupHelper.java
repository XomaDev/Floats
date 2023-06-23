package com.baxolino.apps.floats.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;

import com.baxolino.apps.floats.R;
import com.baxolino.apps.floats.tools.ThemeHelper;

public class PopupHelper {
  /**
   * Moves icons from the PopupMenu's MenuItems' icon fields into the menu title as a Spannable with the icon and title text.
   */
  public static void insertMenuItemIcons(Context context, PopupMenu popupMenu) {
    Menu menu = popupMenu.getMenu();
    if (hasIcon(menu)) {
      for (int i = 0; i < menu.size(); i++) {
        insertMenuItemIcon(context, menu.getItem(i));
      }
    }
  }

  /**
   * @return true if the menu has at least one MenuItem with an icon.
   */
  private static boolean hasIcon(Menu menu) {
    for (int i = 0; i < menu.size(); i++) {
      if (menu.getItem(i).getIcon() != null) return true;
    }
    return false;
  }

  /**
   * Converts the given MenuItem's title into a Spannable containing both its icon and title.
   */
  private static void insertMenuItemIcon(Context context, MenuItem menuItem) {
    Drawable icon = menuItem.getIcon();

    // If there's no icon, we insert a transparent one to keep the title aligned with the items
    // which do have icons.
    if (icon == null) icon = new ColorDrawable(Color.TRANSPARENT);

    int iconSize = context.getResources().getDimensionPixelSize(R.dimen.menu_item_icon_size);

    icon.setTint(
            ThemeHelper.INSTANCE.variant70Color(context)
    );
    icon.setBounds(0, 0, iconSize, iconSize);
    ImageSpan imageSpan = new ImageSpan(icon);

    // Add a space placeholder for the icon, before the title.
    SpannableStringBuilder ssb = new SpannableStringBuilder("       " + menuItem.getTitle());

    // Replace the space placeholder with the icon.
    ssb.setSpan(imageSpan, 1, 2, 0);
    menuItem.setTitle(ssb);
    // Set the icon to null just in case, on some weird devices, they've customized Android to display
    // the icon in the menu... we don't want two icons to appear.
    menuItem.setIcon(null);
  }
}
