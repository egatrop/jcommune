/**
 * Copyright (C) 2011  JTalks.org Team
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.jtalks.jcommune.service.transactional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.testng.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jtalks.common.model.entity.Component;
import org.jtalks.jcommune.model.dao.BannerDao;
import org.jtalks.jcommune.model.entity.Banner;
import org.jtalks.jcommune.model.entity.BannerPosition;
import org.jtalks.jcommune.service.BannerService;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * 
 * @author Anuar_Nurmakanov
 *
 */
public class TransactionalBannerServiceTest {
    @Mock
    private BannerDao bannerDao;
    private BannerService bannerService;
    
    @BeforeMethod
    public void init() {
        initMocks(this);
        bannerService = new TransactionalBannerService(bannerDao);
    }
    
    @Test
    public void newBannerShouldBeSaved() {
        Banner uploadedBanner = new Banner(BannerPosition.TOP, "<html></html>");
        Component component = new Component();
        component.setId(1l);
        
        bannerService.uploadBanner(uploadedBanner, component);
    
        verify(bannerDao).saveOrUpdate(uploadedBanner);
    }
    
    @Test
    public void existBannerShoulBeSavedWithNewContent() {
        String uploadedBannerContent = "<html></html>";
        Banner uploadedBanner = new Banner(BannerPosition.TOP, uploadedBannerContent);
        Banner existsBanner = mock(Banner.class);
        Component component = new Component();
        component.setId(1l);
        when(bannerDao.getByPosition(BannerPosition.TOP)).thenReturn(existsBanner);
        
        bannerService.uploadBanner(uploadedBanner, component);
    
        verify(bannerDao).saveOrUpdate(existsBanner);
        verify(existsBanner).setContent(uploadedBannerContent);
    }
     
    @Test
    public void bannersShouldBeFoundFromRepository() {
        Banner topBanner = new Banner(BannerPosition.TOP, "TOP");
        Banner bottomBanner = new Banner(BannerPosition.BOTTOM, "BOTTOM");
        List<Banner> banners = Arrays.asList(topBanner, bottomBanner);
        when(bannerDao.getAll()).thenReturn(banners);
        
        Map<String, Banner> postionToBannerMap = bannerService.getAllBanners();
       
        verify(bannerDao).getAll();
        assertEquals(postionToBannerMap.get(BannerPosition.TOP.toString()), topBanner);
        assertEquals(postionToBannerMap.get(BannerPosition.BOTTOM.toString()), bottomBanner);
    }
}