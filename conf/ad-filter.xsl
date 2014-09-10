<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ad-filter.xsl
    Created on : 14. Februar 2012, 15:19
    Author     : tstuehler
    Description: Eliminate ad objects.
-->

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output method="xml" indent="no"/>

    <xsl:template match="@*|node()" priority="0.1">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
    
    <xsl:template match="node()|@*" mode="ignore" priority="1">
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>

    <xsl:template match="text()" mode="ignore" priority="1"/>    

    <!-- 20120612 jpm: just ignore the ad objects, but page info are still needed -->
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id='7']">
        <xsl:apply-templates select="child::*" mode="ignore"/>
    </xsl:template>    
    
</xsl:stylesheet>
