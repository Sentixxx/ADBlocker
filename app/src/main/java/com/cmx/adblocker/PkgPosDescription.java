package com.cmx.adblocker;

import java.io.Serializable;
public class PkgPosDescription implements Serializable {
    public String pkgName;
    public String actName;

    public int x;
    public int y;

    public PkgPosDescription() {
        this.pkgName = "";
        this.actName = "";
        this.x = 0;
        this.y = 0;
    }

    public PkgPosDescription(String pkgName,String actName,int x,int y) {
        this.pkgName = pkgName;
        this.actName = actName;
        this.x = x;
        this.y = y;
    }

    public PkgPosDescription(PkgPosDescription posDescription) {
        this.pkgName = posDescription.pkgName;
        this.actName = posDescription.actName;
        this.x = posDescription.x;
        this.y = posDescription.y;
    }
}
