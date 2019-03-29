/**
 * Copyright (c) 2013  Componence Services B.V.(Inc), All Rights Reserved.
 *
 * This software is the confidential and proprietary information of Componence Services B.V
 *
 */

package com.componence.linkedin;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.AutoLogin; 
import com.liferay.portal.security.auth.AutoLoginException;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;

public class LoginHook implements AutoLogin {

	private static Log log = LogFactoryUtil.getLog(LoginHook.class);

	public String[] login(HttpServletRequest request, HttpServletResponse response) throws AutoLoginException {
		String[] credentials = null;

		HttpSession session = request.getSession();
		String email = (String) session.getAttribute("LIFERAY_SHARED_EMAIL");
	
		if (Validator.isNotNull(email) && Validator.isEmailAddress(email)) {
			Long companyId = PortalUtil.getCompanyId(request);
			User user = null;
			try {
				user = UserLocalServiceUtil.getUserByEmailAddress(companyId, email);
			} catch (PortalException e) {
				log.error(e.getMessage());
			} catch (SystemException e) {
				log.error(e.getMessage());
			}

			credentials = new String[3];
			credentials[0] = String.valueOf(user.getUserId());
			credentials[1] = String.valueOf(user.getPassword());
			credentials[2] = Boolean.FALSE.toString();
		}
		return credentials;
	}

}
