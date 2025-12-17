// home.page.ts
import { Component, OnInit, AfterViewInit } from '@angular/core';
import { PushApp } from 'pushapp-ionic';
import { NavController } from '@ionic/angular';
import { Capacitor } from '@capacitor/core';

@Component({
  selector: 'app-home',
  templateUrl: 'home.page.html',
  styleUrls: ['home.page.scss'],
  standalone: false,
})
export class HomePage implements OnInit, AfterViewInit {
  username: string = 'User';

  // 4. Define IDs as constants
  private readonly HTML_BANNER_ID = 'pushapp-placeholder';
  private readonly API_BANNER_ID = 'login_banner';
  
  // ⭐️ NEW TOOLTIP IDs
  private readonly HTML_TOOLTIP_ID = 'tooltip-target';
  private readonly API_TOOLTIP_ID = 'center';

  constructor(private navCtrl: NavController) {}

  ngOnInit() {
    // ... existing login/username logic ...

    // Send page open event (This is where polling often triggers)
    PushApp.sendEvent({
      eventName: 'page_open',
      eventData: { page: 'login' }
    });
    
    // We register both the banner and the tooltip target after the view is ready
  }

  ngAfterViewInit() {
    if (Capacitor.isNativePlatform()) {
      setTimeout(() => {
        // Register the full inline banner view
        this.registerBannerView();
        
        // ⭐️ Register the tooltip target element
        this.registerTooltipTarget(); 
      }, 50);
    }
  }

  // RENAMED for clarity
  async registerBannerView() {
    const placeholderEl = document.getElementById(this.HTML_BANNER_ID);
    if (!placeholderEl) {
        console.error(`Placeholder element #${this.HTML_BANNER_ID} not found.`);
        return;
    }
    
    // Wait for layout to be complete
    await new Promise(resolve => setTimeout(resolve, 100));
    
    const rect = placeholderEl.getBoundingClientRect();
    console.log(`Banner element rect:`, { left: rect.left, top: rect.top, width: rect.width, height: rect.height });
    
    try {
      await PushApp.registerPlaceholder({
        placeholderId: this.API_BANNER_ID,
        x: Math.round(rect.left),
        y: Math.round(rect.top),
        width: Math.round(rect.width),
        height: Math.round(rect.height)
      });
      console.log(`Banner view registered: ${this.API_BANNER_ID} at (${rect.left}, ${rect.top})`);
    } catch (error) {
      console.error('Failed to register banner view:', error);
    }
  }

  // ⭐️ NEW METHOD FOR TOOLTIP TARGET
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
        page: 'dashboard'
      }
    });
  }

  // ... (logout method) ...
}