/**
 * Copyright (c) 2013  Componence Services B.V.(Inc), All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Componence Services B.V
 *
 */

package com.componence.linkedin.action;

import java.awt.image.RenderedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;
import javax.portlet.PortletRequest;
import javax.portlet.ResourceRequest;
import javax.portlet.ResourceResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.liferay.portal.NoSuchUserException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.image.ImageBag;
import com.liferay.portal.kernel.image.ImageToolUtil;
import com.liferay.portal.kernel.io.unsync.UnsyncByteArrayOutputStream;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.struts.BaseStrutsPortletAction;
import com.liferay.portal.kernel.struts.StrutsPortletAction;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.kernel.util.ParamUtil;
import com.liferay.portal.kernel.util.PrefsPropsUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StreamUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.model.ListTypeConstants;
import com.liferay.portal.model.Organization;
import com.liferay.portal.model.OrganizationConstants;
import com.liferay.portal.model.Resource;
import com.liferay.portal.model.ResourceConstants;
import com.liferay.portal.model.Role;
import com.liferay.portal.model.RoleConstants;
import com.liferay.portal.model.User;
import com.liferay.portal.security.permission.ActionKeys;
import com.liferay.portal.service.ClassNameLocalServiceUtil;
import com.liferay.portal.service.OrganizationLocalServiceUtil;
import com.liferay.portal.service.ResourceLocalServiceUtil;
import com.liferay.portal.service.ResourcePermissionLocalServiceUtil;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.service.ServiceContext;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.theme.ThemeDisplay;
import com.liferay.portal.util.PortalUtil;
import com.liferay.portlet.expando.NoSuchTableException;
import com.liferay.portlet.expando.model.ExpandoBridge;
import com.liferay.portlet.expando.model.ExpandoColumn;
import com.liferay.portlet.expando.model.ExpandoColumnConstants;
import com.liferay.portlet.expando.model.ExpandoTable;
import com.liferay.portlet.expando.model.ExpandoTableConstants;
import com.liferay.portlet.expando.model.ExpandoValue;
import com.liferay.portlet.expando.service.ExpandoColumnLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoTableLocalServiceUtil;
import com.liferay.portlet.expando.service.ExpandoValueLocalServiceUtil;
import com.liferay.util.PwdGenerator;

public class LinkedInSignInAction extends BaseStrutsPortletAction {

	private static final String LINKED_IN_CUSTOM_ID = "linkedInCustomId";
	private static final String LINKED_IN_COMPANY_ID = "linkedInCompanyId";
	private static Log log = LogFactoryUtil.getLog(LinkedInSignInAction.class);
	 public static final int USERS_IMAGE_MAX_HEIGHT = GetterUtil
	    .getInteger(PropsUtil.get(PropsKeys.USERS_IMAGE_MAX_HEIGHT));
	 public static final int USERS_IMAGE_MAX_WIDTH = GetterUtil
	    .getInteger(PropsUtil.get(PropsKeys.USERS_IMAGE_MAX_WIDTH));

	public void processAction(StrutsPortletAction originalStrutsPortletAction,
			PortletConfig portletConfig, ActionRequest actionRequest,
			ActionResponse actionResponse) throws Exception {
		long companyId = PortalUtil.getCompanyId(actionRequest);
		String linkedinId = ParamUtil.getString(actionRequest, "idIn");
		User user = getUserByLinkedinId(linkedinId, companyId);

		if (Validator.isNull(user)) {
			String companyNameIn = ParamUtil.getString(actionRequest, "companyNameIn");
			String linkedInCreateOrganizationOption = PrefsPropsUtil.getString(companyId,"linkedin.linkedincreateorganizationoption", "neverJoin");
			String organizationIdIn = ParamUtil.getString(actionRequest,"companyIdIn");
			Organization organization = null;
			//Create and associate organization or associate existing organization
			//for  "Never associate user to matching organization"
			if(!linkedInCreateOrganizationOption.equals("neverJoin")){
				if (Validator.isNotNull(organizationIdIn)) {
					organization = getOrganizationByLinkedinCompanyId(organizationIdIn, companyId);
					//Create and associate organization (for non-existing organization)
					if(linkedInCreateOrganizationOption.equals("createAndJoin")){
						if (Validator.isNull(organization)) {
							organization = processExistingOrgByName(companyId, companyNameIn, organizationIdIn);
							if (Validator.isNull(organization)) {
								organization = createOrganization(companyId, organizationIdIn, companyNameIn);
							}
						}
					}
				}
			}
			user = createUser(actionRequest, companyId, linkedinId, user, organization);
		}
		if (Validator.isNotNull(user)) {
			setExistingUserOnSession(actionRequest,	user);
			ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
			actionResponse.sendRedirect(themeDisplay.getURLSignIn());
		}
	}

	public void serveResource(PortletConfig portletConfig,
			ResourceRequest resourceRequest, ResourceResponse resourceResponse)
			throws Exception {
		long companyId = PortalUtil.getCompanyId(resourceRequest);
		String linkedinId = ParamUtil.getString(resourceRequest, "idIn");
		User user = getUserByLinkedinId(linkedinId, companyId);
		if(user != null && user.getUserId() > 0){
			resourceResponse.setContentType("text/html");
			resourceResponse.getWriter().write("existingUser");
			setExistingUserOnSession(resourceRequest, user);
		}
	}
	
	private void setExistingUserOnSession(PortletRequest portletRequest,
			User user) {
		HttpServletRequest req = PortalUtil.getHttpServletRequest(portletRequest);
		
		HttpSession session = req.getSession();
		session.setAttribute("LIFERAY_SHARED_EMAIL", user.getEmailAddress());
	}
	
	private User createUser(ActionRequest actionRequest, long companyId,
			String linkedinId, User user, Organization organization) {
		String firstName = ParamUtil.getString(actionRequest, "firstNameIn", "");
		String lastName = ParamUtil.getString(actionRequest, "lastNameIn", "");
		String email = ParamUtil.getString(actionRequest, "emailIn", null);
		if(email != null && !email.equals("")){
			try {
				UserLocalServiceUtil.getUserByEmailAddress(companyId, email);
				email = getAutogeneratedEmailAddress(firstName, lastName);
			} catch(NoSuchUserException e){
				// Do nothing
			} catch (Exception e) {
				email = getAutogeneratedEmailAddress(firstName, lastName);
			}
		} else {
			email = getAutogeneratedEmailAddress(firstName, lastName);
		}
		
		String pictureUrl = ParamUtil.getString(actionRequest, "pictureUrlIn", null);
		long creatorUserId = 0;
		boolean autoPassword = true;
		String password1 = StringPool.BLANK;
		String password2 = StringPool.BLANK;
		boolean autoScreenName = true;
		String screenName = StringPool.BLANK;
		long facebookId = 0;
		String openId = StringPool.BLANK;
		Locale locale = LocaleUtil.getDefault();
		String middleName = ParamUtil.getString(actionRequest, "maidenName", "");
		int prefixId = 0;
		int suffixId = 0;
		boolean male = ParamUtil.getBoolean(actionRequest, "gender", true);
		int birthdayMonth = ParamUtil.getInteger(actionRequest,	"month", 1);//-1
		int birthdayDay = ParamUtil.getInteger(actionRequest, "day", 1);
		int birthdayYear = ParamUtil.getInteger(actionRequest, "year",	1970);
		String jobTitle = ParamUtil.getString(actionRequest,"positionsIn");;
		long[] groupIds = null;
		long[] organizationIds = null;
		if (Validator.isNotNull(organization)) {
			organizationIds = new long[1];
			organizationIds[0] = organization.getOrganizationId();
		}
		long[] roleIds = null;
		long[] userGroupIds = null;
		boolean sendEmail = true;

		ServiceContext serviceContext = new ServiceContext();

		try {
			log.debug("Creating user with email "+email);
			user = UserLocalServiceUtil.addUser(creatorUserId, companyId,
					autoPassword, password1, password2, autoScreenName,
					screenName, email, facebookId, openId, locale, firstName,
					middleName, lastName, prefixId, suffixId, male,
					birthdayMonth, birthdayDay, birthdayYear, jobTitle,
					groupIds, organizationIds, roleIds, userGroupIds,
					sendEmail, serviceContext);

			UserLocalServiceUtil.updateLastLogin(user.getUserId(), user.getLoginIP());
			UserLocalServiceUtil.updatePasswordReset(user.getUserId(), false);
			UserLocalServiceUtil.updateEmailAddressVerified(user.getUserId(), true);
			
			/*if(pictureUrl != null && !pictureUrl.equals("")){
				URL profileURL = new URL(pictureUrl);
	            InputStream inputStream = profileURL.openStream();

	            if(inputStream != null){
	            	UnsyncByteArrayOutputStream unsyncByteArrayOutputStream =
	        			new UnsyncByteArrayOutputStream();
	        		StreamUtil.transfer(inputStream, unsyncByteArrayOutputStream, -1, true);
	        		ImageBag imageBag = ImageToolUtil.read(unsyncByteArrayOutputStream.toByteArray());
	                RenderedImage renderedImage = imageBag.getRenderedImage();
	 
	                if (renderedImage != null) {
                        renderedImage = ImageToolUtil.scale(renderedImage,
                                USERS_IMAGE_MAX_HEIGHT, USERS_IMAGE_MAX_WIDTH);
                        String contentType = imageBag.getType();
                        UserLocalServiceUtil.updatePortrait(user.getUserId(), ImageToolUtil.getBytes(renderedImage, contentType));
	                }
            	}
			}*/
			setUserExpandoValue(user, linkedinId, companyId, User.class.getName(),
				LINKED_IN_CUSTOM_ID);
		} catch (Exception e) {
			log.error(e);
		}
		return user;
	}

	private String getAutogeneratedEmailAddress(String firstName,
			String lastName) {
		String email = firstName + "_" + lastName + (new Date().getTime())+ "@liferay.com";
		if(!Validator.isEmailAddress(email)){
			email = PwdGenerator.getPassword(4)+ (new Date().getTime()) + "@liferay.com";
		}
		return email;
	}

	private void setUserExpandoValue(User user, String customFieldValue,
			long companyId, String className, String customFieldName) {
		ExpandoBridge expandoBridge = user.getExpandoBridge();
		setExpandoValue(expandoBridge, customFieldValue, companyId,
				User.class.getName(), customFieldName);
	}

	private void setOrganizationExpandoValue(Organization organization,
			String customFieldValue, long companyId, String className,
			String customFieldName) {
		ExpandoBridge expandoBridge = organization.getExpandoBridge();
		setExpandoValue(expandoBridge, customFieldValue, companyId,
				Organization.class.getName(), customFieldName);
	}

	private void setExpandoValue(ExpandoBridge expandoBridge,
			String customFieldValue, long companyId, String className,
			String customFieldName) {
		ExpandoTable table = null;
		try {
			table = ExpandoTableLocalServiceUtil.getTable(companyId, className,	ExpandoTableConstants.DEFAULT_TABLE_NAME);

		} catch (NoSuchTableException e) {
			try {
				table = ExpandoTableLocalServiceUtil.addTable(companyId, className, ExpandoTableConstants.DEFAULT_TABLE_NAME);
			} catch (Exception ex) {
				log.error(ex.getMessage());
			}
		} catch (Exception ex) {
			log.error(ex.getMessage());
		}
		ExpandoColumn column = null;
		try {
			column = ExpandoColumnLocalServiceUtil.getColumn(companyId,	className, 
					ExpandoTableConstants.DEFAULT_TABLE_NAME, customFieldName);
		} catch (Exception ex) {
			log.error(ex.getMessage());
		}

		if (Validator.isNull(column)) {
			try {
				column = ExpandoColumnLocalServiceUtil.addColumn(
						table.getTableId(), customFieldName, ExpandoColumnConstants.STRING);
				UnicodeProperties properties = expandoBridge.getAttributeProperties(
						customFieldName);
				properties.put("hidden", "1");
				ExpandoColumnLocalServiceUtil.updateTypeSettings(column.getColumnId(), properties.toString());
				Role roleUser = RoleLocalServiceUtil.getRole(companyId,	RoleConstants.GUEST);
				Resource resource = ResourceLocalServiceUtil.addResource(
						companyId, ExpandoColumn.class.getName(),
						ResourceConstants.SCOPE_INDIVIDUAL,
						String.valueOf(column.getColumnId()));

				ResourcePermissionLocalServiceUtil.setResourcePermissions(
						companyId, ExpandoColumn.class.getName(),
						resource.getScope(), resource.getPrimKey(),
						roleUser.getRoleId(), new String[] { ActionKeys.VIEW,
								ActionKeys.DELETE, ActionKeys.UPDATE });
			} catch (Exception ex) {
				log.error(ex.getMessage());
			}
		}
		expandoBridge.setAttribute(customFieldName, customFieldValue, false);
	}

	private Organization createOrganization(long companyId,
			String organizationIdIn, String name) {
		log.debug("Creating organization with name "+name);
		Organization organization = null;
		long parentOrganizationId = OrganizationConstants.DEFAULT_PARENT_ORGANIZATION_ID;
		String type = OrganizationConstants.TYPE_REGULAR_ORGANIZATION;
		boolean recursable = false;
		long regionId = 0;
		long countryId = 0;
		
		ServiceContext serviceContext = new ServiceContext();
		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(true);
		// Actual call to add organization.
		try {
			long defaultUserId = UserLocalServiceUtil.getDefaultUserId(companyId);
			organization = OrganizationLocalServiceUtil.addOrganization(defaultUserId, parentOrganizationId, name, type,
					recursable, regionId, countryId, ListTypeConstants.ORGANIZATION_STATUS_DEFAULT, null,  false ,serviceContext);
			setOrganizationExpandoValue(organization, organizationIdIn,
					companyId, Organization.class.getName(),
					LINKED_IN_COMPANY_ID);
		} catch (PortalException e) {
			log.error(e.getMessage());
		} catch (SystemException e) {
			log.error(e.getMessage());
		}
		return organization;
	}

	private User getUserByLinkedinId(String linkedinId, long companyId) {
		List<ExpandoValue> values;
		try {
			values = ExpandoValueLocalServiceUtil.getColumnValues(companyId,
					ClassNameLocalServiceUtil.getClassNameId(User.class),
					ExpandoTableConstants.DEFAULT_TABLE_NAME,
					LINKED_IN_CUSTOM_ID, linkedinId, -1, -1);

			if (values != null && !values.isEmpty()) {
				long userId = values.get(0).getClassPK();
				return UserLocalServiceUtil.getUser(userId);
			}
		} catch (Exception e) {
			log.error(e.getMessage());
		}
		return null;
	}

	private Organization getOrganizationByLinkedinCompanyId(
			String linkedinCompanyId, long companyId) {
		List<ExpandoValue> values;
		try {
			values = ExpandoValueLocalServiceUtil.getColumnValues(companyId,
					ClassNameLocalServiceUtil
							.getClassNameId(Organization.class),
					ExpandoTableConstants.DEFAULT_TABLE_NAME,
					LINKED_IN_COMPANY_ID, linkedinCompanyId, -1, -1);

			if (values != null && !values.isEmpty()) {
				long organizationId = values.get(0).getClassPK();
				return OrganizationLocalServiceUtil
						.getOrganization(organizationId);
			}
		} catch (Exception e) {
			log.error(e.getMessage());
		}
		return null;
	}
	
	private Organization processExistingOrgByName(long companyId,
			String companyNameIn, String organizationIdIn) {
		Organization organization;
		organization = getOrganizationByLinkedinCompanyName(companyNameIn, companyId);
		if(organization != null && organization.getOrganizationId() > 0){
			setOrganizationExpandoValue(organization, organizationIdIn,	companyId, Organization.class.getName(),LINKED_IN_COMPANY_ID);
		}
		return organization;
	}
	
	private Organization getOrganizationByLinkedinCompanyName(
			String companyNameIn, long companyId) {
		try {
			return OrganizationLocalServiceUtil.getOrganization(companyId, companyNameIn);
		} catch (PortalException e) {
			log.error(e.getMessage());
		} catch (SystemException e) {
			log.error(e.getMessage());
		}
		return null;
	}
}
