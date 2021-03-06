package com.appboy.ui.widget;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.appboy.Appboy;
import com.appboy.configuration.AppboyConfigurationProvider;
import com.appboy.enums.AppboyViewBounds;
import com.appboy.enums.Channel;
import com.appboy.models.cards.Card;
import com.appboy.support.AppboyLogger;
import com.appboy.ui.AppboyNavigator;
import com.appboy.ui.R;
import com.appboy.ui.actions.ActionFactory;
import com.appboy.ui.actions.IAction;
import com.appboy.ui.actions.UriAction;
import com.appboy.ui.feed.AppboyImageSwitcher;
import com.appboy.ui.support.FrescoLibraryUtils;
import com.facebook.drawee.view.SimpleDraweeView;

/**
 * Base class for Braze feed card views
 */
public abstract class BaseCardView<T extends Card> extends RelativeLayout {
  private static final String TAG = AppboyLogger.getAppboyLogTag(BaseCardView.class);
  private static final float SQUARE_ASPECT_RATIO = 1f;
  private static final String ICON_READ_TAG = "icon_read";
  private static final String ICON_UNREAD_TAG = "icon_unread";

  protected final Context mContext;
  private final String mClassLogTag;

  private static Boolean sUnreadCardVisualIndicatorEnabled;
  private static Boolean sCanUseFresco;
  protected T mCard;
  protected AppboyImageSwitcher mImageSwitcher;
  protected AppboyConfigurationProvider mAppboyConfigurationProvider;

  public BaseCardView(Context context) {
    super(context);
    mContext = context.getApplicationContext();

    if (sCanUseFresco == null) {
      // Note: this must be called before we inflate any views.
      sCanUseFresco = FrescoLibraryUtils.canUseFresco(context);
    }

    // Read the setting from the appboy.xml if we don't already have a value.
    if (mAppboyConfigurationProvider == null) {
      mAppboyConfigurationProvider = new AppboyConfigurationProvider(context);
    }
    if (sUnreadCardVisualIndicatorEnabled == null) {
      sUnreadCardVisualIndicatorEnabled = mAppboyConfigurationProvider.getIsNewsfeedVisualIndicatorOn();
    }

    mClassLogTag = AppboyLogger.getAppboyLogTag(this.getClass());
  }

  public void setOptionalTextView(TextView view, String value) {
    if (value != null && !value.trim().equals("")) {
      view.setText(value);
      view.setVisibility(VISIBLE);
    } else {
      view.setText("");
      view.setVisibility(GONE);
    }
  }

  /**
   * Calls setImageViewToUrl with aspect ratio set to 1f and respectAspectRatio set to false.
   * @see com.appboy.ui.widget.BaseCardView#setImageViewToUrl(android.widget.ImageView, String, float, boolean)
   */
  public void setImageViewToUrl(final ImageView imageView, final String imageUrl) {
    setImageViewToUrl(imageView, imageUrl, 1f, false);
  }

  /**
   * Calls setImageViewToUrl with respectAspectRatio set to true.
   * @see com.appboy.ui.widget.BaseCardView#setImageViewToUrl(android.widget.ImageView, String, float, boolean)
   */
  public void setImageViewToUrl(final ImageView imageView, final String imageUrl, final float aspectRatio) {
    setImageViewToUrl(imageView, imageUrl, aspectRatio, true);
  }

  /**
   * Asynchronously fetches the image at the given imageUrl and displays the image in the ImageView. No image will be
   * displayed if the image cannot be downloaded or fetched from the cache.
   *
   * @param imageView the ImageView in which to display the image
   * @param imageUrl the URL of the image resource
   * @param aspectRatio the desired aspect ratio of the image. This should match what's being sent down from the dashboard.
   * @param respectAspectRatio whether to use aspectRatio as the final aspect ratio of the imageView. When set to false,
   *                           the aspect ratio of the imageView will match that of the downloaded image. When set to true,
   *                           the provided aspect ratio will match aspectRatio, regardless of the actual dimensions of the
   *                           downloaded image.
   */
  public void setImageViewToUrl(final ImageView imageView, final String imageUrl, final float aspectRatio, final boolean respectAspectRatio) {
    if (imageUrl == null) {
      AppboyLogger.w(TAG, "The image url to render is null. Not setting the card image.");
      return;
    }

    if (aspectRatio == 0) {
      AppboyLogger.w(TAG, "The image aspect ratio is 0. Not setting the card image.");
      return;
    }

    if (!imageUrl.equals(imageView.getTag(R.string.com_appboy_image_resize_tag_key))) {
      if (aspectRatio != SQUARE_ASPECT_RATIO) {
        // We need to set layout params on the imageView once its layout state is visible. To do this,
        // we obtain the imageView's observer and attach a listener on it for when the view's layout
        // occurs. At layout time, we set the imageView's size params based on the aspect ratio
        // for our card. Note that after the card's first layout, we don't want redundant resizing
        // so we remove our listener after the resizing.
        ViewTreeObserver viewTreeObserver = imageView.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
          viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
              int width = imageView.getWidth();
              imageView.setLayoutParams(new LayoutParams(width, (int) (width / aspectRatio)));
              imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
          });
        }
      }

      imageView.setImageResource(android.R.color.transparent);
      Appboy.getInstance(getContext()).getAppboyImageLoader().renderUrlIntoView(getContext(), imageUrl, imageView, AppboyViewBounds.BASE_CARD_VIEW);
      imageView.setTag(R.string.com_appboy_image_resize_tag_key, imageUrl);
    }
  }

  /**
   * Loads an image via url for display in a SimpleDraweeView using the Facebook Fresco library.
   * By default, gif urls are set to autoplay and tap to retry is on for all images.
   * @param simpleDraweeView the fresco SimpleDraweeView in which to display the image
   * @param imageUrl the URL of the image resource
   */
  public void setSimpleDraweeToUrl(final SimpleDraweeView simpleDraweeView, final String imageUrl, final float aspectRatio, final boolean respectAspectRatio) {
    if (imageUrl == null) {
      AppboyLogger.w(getClassLogTag(), "The image url to render is null. Not setting the card image.");
      return;
    }

    FrescoLibraryUtils.setDraweeControllerHelper(simpleDraweeView, imageUrl, aspectRatio, respectAspectRatio);
  }

  /**
   * Checks to see if the card object is viewed and if so, sets the read/unread status
   * indicator image. If the card is null, does nothing.
   */
  public void setCardViewedIndicator(AppboyImageSwitcher imageSwitcher, Card card) {
    if (card == null) {
      AppboyLogger.d(getClassLogTag(), "The card is null. Not setting read/unread indicator.");
      return;
    }

    if (imageSwitcher == null) {
      return;
    }

    // Check the tag for the image switcher so we don't have to re-draw the same indicator unnecessarily
    String imageSwitcherTag = (String) imageSwitcher.getTag();
    // If the tag is null, default to the empty string
    imageSwitcherTag = imageSwitcherTag != null ? imageSwitcherTag : "";

    if (card.isRead()) {
      if (!imageSwitcherTag.equals(ICON_READ_TAG)) {
        if (imageSwitcher.getReadIcon() != null) {
          imageSwitcher.setImageDrawable(imageSwitcher.getReadIcon());
        } else {
          imageSwitcher.setImageResource(R.drawable.icon_read);
        }
        imageSwitcher.setTag(ICON_READ_TAG);
      }
    } else {
      if (!imageSwitcherTag.equals(ICON_UNREAD_TAG)) {
        if (imageSwitcher.getUnReadIcon() != null) {
          imageSwitcher.setImageDrawable(imageSwitcher.getUnReadIcon());
        } else {
          imageSwitcher.setImageResource(R.drawable.icon_unread);
        }
        imageSwitcher.setTag(ICON_UNREAD_TAG);
      }
    }
  }

  public String getClassLogTag() {
    return mClassLogTag;
  }

  /**
   * Returns whether we can use the Fresco Library for newsfeed cards.
   */
  public boolean canUseFresco() {
    return sCanUseFresco;
  }

  public boolean isUnreadIndicatorEnabled() {
    return sUnreadCardVisualIndicatorEnabled;
  }

  /**
   * Calls the corresponding card manager to see if the action listener has handled the click.
   */
  protected abstract boolean isClickHandled(Context context, Card card, IAction cardAction);

  protected static UriAction getUriActionForCard(Card card) {
    Bundle extras = new Bundle();
    for (String key : card.getExtras().keySet()) {
      extras.putString(key, card.getExtras().get(key));
    }
    return ActionFactory.createUriActionFromUrlString(card.getUrl(), extras, card.getOpenUriInWebView(), Channel.NEWS_FEED);
  }

  protected void handleCardClick(Context context, Card card, IAction cardAction, String tag) {
    card.setIsRead(true);
    if (cardAction != null) {
      if (card.logClick()) {
        AppboyLogger.d(tag, "Logged click for card: " + card.getId());
      } else {
        AppboyLogger.d(tag, "Logging click failed for card: " + card.getId());
      }
      if (!isClickHandled(context, card, cardAction)) {
        if (cardAction instanceof UriAction) {
          AppboyNavigator.getAppboyNavigator().gotoUri(context, (UriAction) cardAction);
        } else {
          Log.d(TAG, "Executing non uri action for click on card: " + card.getId());
          cardAction.execute(context);
        }
      } else {
        AppboyLogger.d(TAG, "Card click was handled by custom listener for card: " + card.getId());
      }
    } else {
      AppboyLogger.v(TAG, "Card action is null. Not performing any click action for card: " + card.getId());
    }
  }
}
