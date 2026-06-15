// home.page.ts
import { Component, OnInit } from '@angular/core';
import { PushApp } from 'pushapp-ionic';
import { NavController } from '@ionic/angular';
import { Capacitor } from '@capacitor/core';

interface ProductCard {
  name: string;
  brand: string;
  price: string;
  rating: string;
  emoji: string;
  gradient: string;
}

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
  standalone: false,
})
export class HomePage implements OnInit {
  username = 'User';

  get displayName(): string {
    const name = this.username.trim();
    if (!name) return 'Shopper';
    return name.charAt(0).toUpperCase() + name.slice(1);
  }

  stats = [
    { icon: 'heart-outline', value: '12', label: 'Saved' },
    { icon: 'pricetag-outline', value: '3', label: 'Deals' },
    { icon: 'cube-outline', value: '2', label: 'Orders' },
  ];

  products: ProductCard[] = [
    { name: 'Aero Runner Sneakers', brand: 'Nimbus', price: '$129', rating: '4.8', emoji: '👟', gradient: 'linear-gradient(145deg, #e0e7ff, #c7d2fe)' },
    { name: 'Linen Weekend Tote', brand: 'Harbor', price: '$68', rating: '4.6', emoji: '👜', gradient: 'linear-gradient(145deg, #fce7f3, #fbcfe8)' },
    { name: 'Ceramic Pour-Over Kit', brand: 'Brew & Co', price: '$54', rating: '4.9', emoji: '☕', gradient: 'linear-gradient(145deg, #d1fae5, #a7f3d0)' },
    { name: 'Noise-Cancel Headphones', brand: 'Pulse', price: '$249', rating: '4.7', emoji: '🎧', gradient: 'linear-gradient(145deg, #fef3c7, #fde68a)' },
  ];

  // 4. Define IDs as constants
  private readonly PLACEHOLDER_ID = 'wave-placeholder';
  
  // ⭐️ NEW TOOLTIP IDs
  private readonly HTML_TOOLTIP_ID = 'tooltip-target';
  private readonly API_TOOLTIP_ID = 'center';

  constructor(private navCtrl: NavController) {}

  ngOnInit() {
    const savedUsername = localStorage.getItem('username');
    if (savedUsername) {
      this.username = savedUsername;
    }

    // Send page open event (This is where polling often triggers)
    PushApp.sendEvent({
      eventName: 'page_open',
      eventData: { page: 'watchlist' }
    });
  }

  ionViewDidEnter() {
    PushApp.setPageName({ pageName: 'watchlist' });

    if (Capacitor.isNativePlatform()) {
      requestAnimationFrame(() => {
        setTimeout(() => {
          this.registerBannerView();
          this.registerTooltipTarget();
        }, 250);
      });
    }
  }

  ionViewWillLeave() {
    if (Capacitor.isNativePlatform()) {
      PushApp.unregisterPlaceholder({ placeholderId: this.PLACEHOLDER_ID }).catch(() => undefined);
    }
  }

  async registerBannerView() {
    try {
      await PushApp.registerPlaceholder({ placeholderId: this.PLACEHOLDER_ID });
      console.log(`Banner placeholder registered: ${this.PLACEHOLDER_ID}`);
    } catch (error) {
      console.error('Failed to register banner view:', error);
    }
  }

  async registerTooltipTarget() {
    const targetEl = document.getElementById(this.HTML_TOOLTIP_ID);

    if (!targetEl) {
      console.error(`Tooltip target element #${this.HTML_TOOLTIP_ID} not found in DOM.`);
      return;
    }

    // Wait for layout to be complete and ensure element is visible
    await new Promise(resolve => setTimeout(resolve, 200));
    
    // Scroll element into view to ensure accurate coordinates
    targetEl.scrollIntoView({ behavior: 'instant', block: 'nearest' });
    await new Promise(resolve => setTimeout(resolve, 50));

    // Get the element's position and size
    const rect = targetEl.getBoundingClientRect();
    const scrollX = window.scrollX || window.pageXOffset || 0;
    const scrollY = window.scrollY || window.pageYOffset || 0;
    
    console.log(`Tooltip target rect:`, { 
      left: rect.left, 
      top: rect.top, 
      width: rect.width, 
      height: rect.height,
      scrollX: scrollX,
      scrollY: scrollY,
      windowInnerHeight: window.innerHeight,
      windowInnerWidth: window.innerWidth
    });

    try {
      // ⭐️ Call a new, dedicated native method for tooltips/popovers
      // getBoundingClientRect() gives viewport-relative coordinates (accounts for scroll)
      const result = await PushApp.registerTooltipTarget({
        targetId: this.API_TOOLTIP_ID,
        x: Math.round(rect.left),
        y: Math.round(rect.top),
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      });
      
      console.log('✅ Tooltip target registered:', result, `at viewport (${rect.left}, ${rect.top})`);
    } catch (error) {
      console.error('Failed to register tooltip target:', error);
    }
  }

  onCircleButtonClick() {
    console.log('Circle button clicked! (Tooltip check event)');
    
    // ⭐️ Send a specific event that will trigger the tooltip poll response
    PushApp.sendEvent({
      eventName: 'circle_button_clicked',
      eventData: {
        username: this.username,
        page: 'watchlist'
      }
    });
  }

  logout() {
    localStorage.removeItem('username');
    this.navCtrl.navigateRoot('/login');
  }
}