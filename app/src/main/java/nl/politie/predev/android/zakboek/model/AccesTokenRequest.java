package nl.politie.predev.android.zakboek.model;

import java.util.Date;

public class AccesTokenRequest {

	public static AccesTokenRequest accesTokenRequest;
	public static Date requested_at = null;

    private String accessToken;
    private String tokenType;

    public static boolean shouldRefresh(){
    	if(requested_at == null) {
    		return true;
		}
    	//9 minuten, want 10 minuten is max
    	if((new Date().getTime() - requested_at.getTime()) > (1000 * 9 * 60)) {
    		return true;
		}
    	return false;
	}

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }




}
