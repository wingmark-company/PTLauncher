package org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.logging.logger.Logger;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.GameProfile;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.HttpUserAuthentication;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.exceptions.AuthenticationException;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.exceptions.InvalidCredentialsException;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request.AuthenticationRequest;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.request.RefreshRequest;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.AuthenticationResponse;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.RefreshResponse;
import org.jackhuang.hellominecraft.launcher.utils.auth.yggdrasil.response.User;

public class YggdrasilUserAuthentication extends HttpUserAuthentication {

    private static final Logger LOGGER = new Logger("YggdrasilUserAuthentication");
    private static final String BASE_URL = "https://authserver.mojang.com/";
    private static final URL ROUTE_AUTHENTICATE = NetUtils.constantURL(BASE_URL + "authenticate");
    private static final URL ROUTE_REFRESH = NetUtils.constantURL(BASE_URL + "refresh");
    private static final String STORAGE_KEY_ACCESS_TOKEN = "accessToken";
    private GameProfile[] profiles;
    private String accessToken;
    private boolean isOnline;

    public YggdrasilUserAuthentication(YggdrasilAuthenticationService authenticationService) {
	super(authenticationService);
    }

    @Override
    public boolean canLogIn() {
	return (!canPlayOnline()) && (StrUtils.isNotBlank(getUsername())) && ((StrUtils.isNotBlank(getPassword())) || (StrUtils.isNotBlank(getAuthenticatedToken())));
    }

    @Override
    public void logIn() throws AuthenticationException {
	if (StrUtils.isBlank(getUsername())) {
	    throw new InvalidCredentialsException(C.i18n("login.invalid_username"));
	}

	if (StrUtils.isNotBlank(getAuthenticatedToken())) {
	    logInWithToken();
	} else if (StrUtils.isNotBlank(getPassword())) {
	    logInWithPassword();
	} else {
	    throw new InvalidCredentialsException(C.i18n("login.invalid_password"));
	}
    }

    protected void logInWithPassword() throws AuthenticationException {
	if (StrUtils.isBlank(getUsername())) {
	    throw new InvalidCredentialsException(C.i18n("login.invalid_username"));
	}
	if (StrUtils.isBlank(getPassword())) {
	    throw new InvalidCredentialsException(C.i18n("login.invalid_password"));
	}

	LOGGER.info("Logging in with username & password");

	AuthenticationRequest request = new AuthenticationRequest(this, getUsername(), getPassword());
	AuthenticationResponse response = (AuthenticationResponse) getAuthenticationService().makeRequest(ROUTE_AUTHENTICATE, request, AuthenticationResponse.class);

	if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
	    throw new AuthenticationException(C.i18n("login.changed_client_token"));
	}

	User user = response.getUser();

	if ((user != null) && (user.getId() != null)) {
	    setUserid(user.getId());
	} else {
	    setUserid(getUsername());
	}

	this.isOnline = true;
	this.accessToken = response.getAccessToken();

	this.profiles = response.getAvailableProfiles();
	setSelectedProfile(response.getSelectedProfile());
	getModifiableUserProperties().clear();

	updateUserProperties(user);
    }

    protected void updateUserProperties(User user) {
	if (user == null) {
	    return;
	}

	if (user.getProperties() != null) {
	    getModifiableUserProperties().putAll(user.getProperties());
	}
    }

    protected void logInWithToken() throws AuthenticationException {
	if (StrUtils.isBlank(getUserID())) {
	    if (StrUtils.isBlank(getUsername())) {
		setUserid(getUsername());
	    } else {
		throw new InvalidCredentialsException(C.i18n("login.invalid_uuid_and_username"));
	    }
	}
	if (StrUtils.isBlank(getAuthenticatedToken())) {
	    throw new InvalidCredentialsException(C.i18n("login.invalid_access_token"));
	}

	LOGGER.info("Logging in with access token");

	RefreshRequest request = new RefreshRequest(this);
	RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);

	if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
	    throw new AuthenticationException(C.i18n("login.changed_client_token"));
	}

	if ((response.getUser() != null) && (response.getUser().getId() != null)) {
	    setUserid(response.getUser().getId());
	} else {
	    setUserid(getUsername());
	}

	this.isOnline = true;
	this.accessToken = response.getAccessToken();
	this.profiles = response.getAvailableProfiles();
	setSelectedProfile(response.getSelectedProfile());
	getModifiableUserProperties().clear();

	updateUserProperties(response.getUser());
    }

    @Override
    public void logOut() {
	super.logOut();

	this.accessToken = null;
	this.profiles = null;
	this.isOnline = false;
    }

    @Override
    public GameProfile[] getAvailableProfiles() {
	return this.profiles;
    }

    @Override
    public boolean isLoggedIn() {
	return StrUtils.isNotBlank(this.accessToken);
    }

    @Override
    public boolean canPlayOnline() {
	return (isLoggedIn()) && (getSelectedProfile() != null) && (this.isOnline);
    }

    @Override
    public void selectGameProfile(GameProfile profile) throws AuthenticationException {
	if (!isLoggedIn()) {
	    throw new AuthenticationException(C.i18n("login.profile.not_logged_in"));
	}
	if (getSelectedProfile() != null) {
	    throw new AuthenticationException(C.i18n("login.profile.selected"));
	}
	if ((profile == null) || (!ArrayUtils.contains(this.profiles, profile))) {
	    throw new IllegalArgumentException("Invalid profile '" + profile + "'");
	}

	RefreshRequest request = new RefreshRequest(this, profile);
	RefreshResponse response = (RefreshResponse) getAuthenticationService().makeRequest(ROUTE_REFRESH, request, RefreshResponse.class);

	if (!response.getClientToken().equals(getAuthenticationService().getClientToken())) {
	    throw new AuthenticationException(C.i18n("login.changed_client_token"));
	}

	this.isOnline = true;
	this.accessToken = response.getAccessToken();
	setSelectedProfile(response.getSelectedProfile());
    }

    @Override
    public void loadFromStorage(Map<String, Object> credentials) {
	super.loadFromStorage(credentials);

	this.accessToken = (String)credentials.get(STORAGE_KEY_ACCESS_TOKEN);
    }

    @Override
    public Map<String, Object> saveForStorage() {
	Map result = super.saveForStorage();

	if (StrUtils.isNotBlank(getAuthenticatedToken())) {
	    result.put("accessToken", getAuthenticatedToken());
	}

	return result;
    }

    @Deprecated
    public String getSessionToken() {
	if ((isLoggedIn()) && (getSelectedProfile() != null) && (canPlayOnline())) {
	    return String.format("token:%s:%s", new Object[]{getAuthenticatedToken(), getSelectedProfile().getId()});
	}
	return null;
    }

    @Override
    public String getAuthenticatedToken() {
	return this.accessToken;
    }

    @Override
    public String toString() {
	return "YggdrasilAuthenticationService{profiles=" + Arrays.toString(this.profiles) + ", selectedProfile=" + getSelectedProfile() + ", username='" + getUsername() + '\'' + ", isLoggedIn=" + isLoggedIn() + ", canPlayOnline=" + canPlayOnline() + ", accessToken='" + this.accessToken + '\'' + ", clientToken='" + getAuthenticationService().getClientToken() + '\'' + '}';
    }

    @Override
    public YggdrasilAuthenticationService getAuthenticationService() {
	return (YggdrasilAuthenticationService) super.getAuthenticationService();
    }
}