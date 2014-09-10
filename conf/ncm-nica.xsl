<?xml version="1.0" encoding="UTF-8"?>

<!--
    Document   : ncm-nica.xsl
    Created on : 17. Februar 2012, 11:40
    Author     : tstuehler
    Description:
        Purpose of transformation follows.
    Revision History:
        20140311 jpm - added new metadata fields (these are the fields entered through the custom metadata GUI)
        20130919 jpm - use different xpaths for getting metadata, based on the pub
                        -using local:metadataNamingMode local function    
                     - strip space for "par" elements
        20130409 jpm add page metadata: Redraws, ReasonRedraw
        20130404 jpm in 'content' templates, allow facility to determine whether mediatype is Incopy or not
                    so if there's a need to have different handling for Adobe vs Newsroom, this can be done.
        20130328 jpm eliminate object duplicates
        20130321 jpm eliminate duplicates when processing packages and standalone objects
        20130218 jpm 1. modification of special char handling
                        - to also include non-glyph styles that may be using special char fonts (e.g. EuropeanPi)
                     2. add computation of word count for Adobe environment
        20130110 jpm add handling for headlines in an Adobe environment
        20121126 jpm corrections in setting of copyright
                    - if story came from the wires, set to NONSPH
                    - else, use OBJ_COPYRIGHT metadata value (if present)
                    - else, set to SPH as default
        20120919 jpm select metadata from correct publication metadata group
        20120727 jpm 1. set copyright to 'NONSPH' if story came from the wires
        20120725 jpm 1. for images, added processing instructions to save image crop and transform info
        20120722 jpm 1. kicker - get from Supertitle component of headline, but only if the Maintitle component has content
                     2. teaser and subtitle components of headline go to summary
        20120720 jpm added special char map lookup for glyphs    
        20120719 jpm replace reserved chars [ and ] (used for tags) with markers.
        20120717 jpm handle merge copy tags (MC): remove '_MCn' suffix, e.g. CAPTION_MC1 -> CAPTION    
        20120626 jpm include export of standalone objects (objects not part of a package),
            specifically images and graphics
-->

<xsl:stylesheet version="2.0" 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:fn="http://www.w3.org/2005/xpath-functions"
    xmlns:xdt="http://www.w3.org/2005/xpath-datatypes"
    xmlns:err="http://www.w3.org/2005/xqt-errors"
    xmlns:local="http://www.atex.de/local"
    exclude-result-prefixes="xsl xs xdt err fn local">

    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>
    <xsl:strip-space elements="par"/>

    <xsl:param name="partyName" select="'Batch'"/>
    <xsl:param name="providerId" select="'atex'"/>
    <xsl:param name="itemRefPrefix" select="'urn:newsml:atex:'"/>
    <xsl:param name="copyright" select="'SPH'"/>
    <xsl:param name="pagePDFPath" select="'/pagePDFPath/property/is/missing/'"/>
    <xsl:param name="isPrinted" select="'false'"/>
    <xsl:param name="exportStandaloneObjects" select="'false'"/>
    <xsl:param name="specialCharMap" select="''"/>
    
    <xsl:variable name="specialCharMapDoc" select="document($specialCharMap)/lookup"/>
    
    <xsl:template match="/">
        <xsl:element name="Pages">
            <xsl:apply-templates select="//ncm-physical-page"/>
        </xsl:element>
    </xsl:template>

    <!-- <xsl:template match="ncm-physical-page[logical-pages-linked-to-ncm-physical-page[@count>0]/ncm-logical-page/layouts-in-ncm-logical-page/@count>0]"> -->
    <!-- CAUTION: layouts-in-ncm-logical-page/@count seems to be always one off -->
    <!-- <xsl:template match="ncm-physical-page[logical-pages-linked-to-ncm-physical-page[@count>0]/ncm-logical-page/layouts-in-ncm-logical-page/@count>1]"> -->
    <!-- <xsl:template match="ncm-physical-page[logical-pages-linked-to-ncm-physical-page[@count>0]/ncm-logical-page/layouts-in-ncm-logical-page/@count>1 and ($isPrinted!='true' or ($isPrinted='true' and is_printed='true'))]"> -->
    <xsl:template match="ncm-physical-page[logical-pages-linked-to-ncm-physical-page[@count>0]/ncm-logical-page/layouts-in-ncm-logical-page/@count>0 and ($isPrinted!='true' or ($isPrinted='true' and is_printed='true'))]">
        <xsl:variable name="pub" select="./edition/newspaper-level/level/@name"/>
        <xsl:element name="NewsML">
            <xsl:element name="NewsEnvelope">
                <xsl:element name="SentFrom">
                    <xsl:element name="Party">
                        <xsl:attribute name="FormalName" select="$partyName"/>
                    </xsl:element>
                </xsl:element>
                <xsl:element name="DateAndTime"><xsl:value-of select="fn:format-dateTime(fn:current-dateTime(), '[Y0001][M01][D01]T[H01][m01][s01]')"/></xsl:element>
            </xsl:element>
            <xsl:element name="NewsItem">
                <xsl:element name="Identification">
                    <xsl:element name="NewsIdentifier">
                        <xsl:element name="ProviderId"><xsl:value-of select="$providerId"/></xsl:element>
                        <xsl:element name="DateId"><xsl:value-of select="local:convertNcmDate(./pub_date)"/></xsl:element>
                        <xsl:element name="NewsItemId"><xsl:value-of select="concat('physicalpage-', ./phpage_id)"/></xsl:element>
                        <xsl:element name="RevisionId">
                            <xsl:attribute name="PreviousRevision">0</xsl:attribute>
                            <xsl:attribute name="Update">N</xsl:attribute>
                            <xsl:text>1</xsl:text>
                        </xsl:element>
                        <xsl:element name="PublicIdentifier"><xsl:value-of select="concat($itemRefPrefix, local:convertNcmDate(./pub_date), '::', ./edition/newspaper-level/level/@name, ':physicalpage-', ./phpage_id)"/></xsl:element>
                    </xsl:element>
                </xsl:element>
                <xsl:element name="NewsManagement">
                    <xsl:element name="NewsItemType">
                        <xsl:attribute name="FormalName">Page</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="FirstCreated"><xsl:value-of select="local:convertNcmDate(./creation_ts)"/></xsl:element>
                    <xsl:element name="ThisRevisionCreated"><xsl:value-of select="local:convertNcmDate(./modifier_ts)"/></xsl:element>
                    <xsl:element name="Status">
                        <xsl:attribute name="FormalName"><xsl:value-of select="./physpage_status/status/@name"/></xsl:attribute>
                    </xsl:element>
                </xsl:element>
                <xsl:element name="NewsComponent">
                    <xsl:element name="Metadata">
                        <xsl:element name="MetadataType">
                            <xsl:attribute name="FormalName">PublicationInfo</xsl:attribute>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Checker</xsl:attribute>
                            <xsl:choose>
                                <xsl:when test="local:metadataNamingMode($pub)='1'">
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/PAGE/CHECKER)[1]"/>                                                                        
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/*[name()=$pub]/PAGE_CHECKER)[1]"/>                                    
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Designer</xsl:attribute>
                            <xsl:choose>
                                <xsl:when test="local:metadataNamingMode($pub)='1'">
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/PAGE/DESIGNER)[1]"/>                                                                        
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/*[name()=$pub]/PAGE_DESIGNER)[1]"/>                                    
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">numPages</xsl:attribute>
                            <xsl:attribute name="Value">1</xsl:attribute>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">PageName</xsl:attribute>
                            <xsl:attribute name="Value" select="replace(./note, '&lt;.*?&gt;', '')"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Edition</xsl:attribute>
                            <xsl:attribute name="Value" select="./edition/@name"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">MajorProductName</xsl:attribute>
                            <xsl:attribute name="Value" select="./edition/newspaper-level/level/@name"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">MinorProductName</xsl:attribute>
                            <xsl:attribute name="Value" select="./user2"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">NumericPageNumber</xsl:attribute>
                            <!-- <xsl:attribute name="Value" select="./seq_number"/> -->
                            <xsl:attribute name="Value" select="local:crPageNum('', ./page_number_str, ./page_number1_str)"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">PageNumber</xsl:attribute>
                            <!-- <xsl:attribute name="Value" select="./page_number_str"/> -->
                            <xsl:attribute name="Value" select="local:crPageNum(./section/@name, ./page_number_str, ./page_number1_str)"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">PhysPageId</xsl:attribute>
                            <xsl:attribute name="Value" select="./phpage_id"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">PubDate</xsl:attribute>
                            <xsl:attribute name="Value" select="local:convertNcmDate(./pub_date)"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Publication</xsl:attribute>
                            <xsl:attribute name="Value" select="./edition/newspaper-level/level/@name"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Section</xsl:attribute>
                            <xsl:attribute name="Value" select="./section1/@name"/>
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">Redraws</xsl:attribute>
                            <xsl:choose>
                                <xsl:when test="local:metadataNamingMode($pub)='1'">
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/PAGE/REDRAW)[1]"/>                                                                        
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/*[name()=$pub]/PAGE_REDRAW)[1]"/>                                    
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:element>
                        <xsl:element name="Property">
                            <xsl:attribute name="FormalName">ReasonRedraw</xsl:attribute>
                            <xsl:choose>
                                <xsl:when test="local:metadataNamingMode($pub)='1'">
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/PAGE/REDRAW_REASON)[1]"/>                                                                        
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:attribute name="Value" select="(./logical-pages-linked-to-ncm-physical-page/ncm-logical-page/extra-properties/*[name()=$pub]/PAGE_REDRAW_REASON)[1]"/>                                    
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:element>                                                
                    </xsl:element>
                    <xsl:element name="NewsComponent">
                        <xsl:element name="ContentItem">
                            <xsl:attribute name="Href" select="concat(./phpage_id, '_', (.//ncm-logical-page/page_id)[1], '.pdf')"/>
                        </xsl:element>
                        <!-- 20120618 PDF file name changed back to: <phpage_id>_<lpage_id>.<ext> -->
                        <!-- <xsl:processing-instruction name="ncm-highrespath" select="concat($pagePDFPath, local:convertNcmDate(./pub_date), '_', ./edition/newspaper-level/level/@name, '_', ./edition/@name, '_', ./seq_number, '_', (.//ncm-logical-page/name)[1], '.pdf')"/> -->
                        <xsl:processing-instruction name="highres-imagepath" select="concat($pagePDFPath, ./phpage_id, '_', (.//ncm-logical-page/page_id)[1], '.pdf')"/>
                    </xsl:element>
                    <!-- packages -->
                    <xsl:apply-templates select=".//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[ncm-type-property/object-type/@id=17]" mode="component">
                        <xsl:with-param name="physPage" select="."/>
                    </xsl:apply-templates>
                    <!-- standalone objects - not part of a package -->
                    <xsl:if test="$exportStandaloneObjects='true'">
                        <xsl:apply-templates select=".//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=0 and ncm-type-property/object-type/@id!=17 and local:exportStandaloneObjType(ncm-type-property/object-type/@id)=1]" mode="standalone-component">
                            <xsl:with-param name="physPage" select="."/>
                        </xsl:apply-templates>                        
                    </xsl:if>
                </xsl:element>
            </xsl:element>
            <!-- packages -->
            <xsl:apply-templates select=".//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[ncm-type-property/object-type/@id=17]" mode="item">
                <xsl:with-param name="physPage" select="."/>
            </xsl:apply-templates>
            <!-- standalone objects - not part of a package -->
            <xsl:if test="$exportStandaloneObjects='true'">
                <xsl:apply-templates select=".//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=0 and ncm-type-property/object-type/@id!=17 and local:exportStandaloneObjType(ncm-type-property/object-type/@id)=1]" mode="standalone-item">
                    <xsl:with-param name="physPage" select="."/>
                </xsl:apply-templates>                                        
            </xsl:if>            
        </xsl:element>
    </xsl:template>
    
    <!-- package component -->
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=17]" mode="component">
        <xsl:param name="physPage"/>
        <xsl:variable name="spId" select="./obj_id"/>
        <xsl:element name="NewsComponent">
            <xsl:element name="Role">
                <xsl:attribute name="FormalName">Article</xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="NewsItemRef">
                    <xsl:attribute name="NewsItem" select="local:crPkgRefId($physPage, .)"/>
                </xsl:element>
            </xsl:element>
            <xsl:if test="local:hasTextualObjs($physPage, $spId) = 1">
                <!-- Text component -->
                <!-- create only one Text component per package -->
                <xsl:call-template name="createTextComponent">
                    <xsl:with-param name="textObjs" select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=1]"/>
                    <xsl:with-param name="physPage" select="$physPage"/>
                    <xsl:with-param name="storyPackage" select="."/>
                </xsl:call-template>                
            </xsl:if>
            <!-- non-Text components -->
            <xsl:apply-templates select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$spId and ncm-type-property/object-type/@id!=17 and ncm-type-property/object-type/@id!=1]" mode="component">
                <xsl:with-param name="physPage" select="$physPage"/>                
            </xsl:apply-templates>
        </xsl:element>
    </xsl:template>
    
    <!-- standalone object component -->
    <xsl:template match="ncm-object" mode="standalone-component">
        <xsl:param name="physPage"/>
        <xsl:variable name="objId" select="./obj_id"/>
        <xsl:element name="NewsComponent">
            <xsl:element name="Role">
                <xsl:attribute name="FormalName">Article</xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="NewsItemRef">
                    <xsl:attribute name="NewsItem" select="local:crPkgRefId($physPage, .)"/>
                </xsl:element>
            </xsl:element>
            <!-- there are no Text components -->
            <!-- non-Text components -->
            <xsl:apply-templates select="." mode="component">
                <xsl:with-param name="physPage" select="$physPage"/>                
            </xsl:apply-templates>
        </xsl:element>
    </xsl:template>    
      
    <xsl:template name="createTextComponent">
        <xsl:param name="textObjs"/>
        <xsl:param name="physPage"/>
        <xsl:param name="storyPackage"/>
        <xsl:variable name="mainObj" select="local:getMainObj($textObjs, $storyPackage)"/>
        <xsl:element name="NewsComponent">
            <xsl:element name="Role">
                <xsl:attribute name="FormalName">Text</xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="NewsItemRef">
                    <xsl:attribute name="NewsItem" select="local:crRefId($physPage, $mainObj)"/>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="ContentItem">
                    <xsl:attribute name="Href" select="local:crFileName('Text', $physPage, $mainObj, 'txt')"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9]" mode="component">
        <xsl:param name="physPage"/>
        <xsl:variable name="typeName">
            <xsl:choose>
                <xsl:when test="ncm-type-property/object-type/@id=6">Image</xsl:when>
                <xsl:when test="ncm-type-property/object-type/@id=9">Graphic</xsl:when>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="typeExt">
            <xsl:choose>
                <xsl:when test="ncm-type-property/object-type/@id=6">jpg</xsl:when>
                <xsl:when test="ncm-type-property/object-type/@id=9">pdf</xsl:when>
            </xsl:choose>
        </xsl:variable>        
        <xsl:element name="NewsComponent">
            <xsl:element name="Role">
                <xsl:attribute name="FormalName"><xsl:value-of select="$typeName"/></xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="NewsItemRef">
                    <xsl:attribute name="NewsItem" select="local:crRefId($physPage, .)"/>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="ContentItem">
                    <xsl:attribute name="Href" select="local:crFileName($typeName, $physPage, ., $typeExt)"/>
                </xsl:element>
                <xsl:processing-instruction name="highres-imagepath" select=".//file-property[@type='NCMImage']/original-file/server-path"/>
                <xsl:processing-instruction name="medres-imagepath" select=".//file-property[@type='NCMImage']/medium-preview/server-path"/>
                <xsl:processing-instruction name="lowres-imagepath" select=".//file-property[@type='NCMImage']/low-preview/server-path"/>
                <xsl:processing-instruction name="dimension" select="concat(./content-property/image-size/width, ' ', ./content-property/image-size/height)"/>
                <xsl:if test="./content-property/crop-rect">
                    <xsl:processing-instruction name="crop-rect" select="concat(./content-property/crop-rect/@bottom, ' ', ./content-property/crop-rect/@left, ' ', ./content-property/crop-rect/@top, ' ', ./content-property/crop-rect/@right)"/>
                </xsl:if>
                <xsl:if test="./content-property/xy-transf">
                    <xsl:processing-instruction name="rotate" select="./content-property/xy-transf/@rotate"/>
                    <xsl:processing-instruction name="flip-x" select="./content-property/xy-transf/@flip-x"/>
                    <xsl:processing-instruction name="flip-y" select="./content-property/xy-transf/@flip-y"/>
                </xsl:if>
            </xsl:element>
        </xsl:element>
    </xsl:template>
    
    <!-- package item -->
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=17]" mode="item">
        <xsl:param name="physPage"/>
        <xsl:element name="NewsItem">
            <xsl:element name="Identification">
                <xsl:element name="NewsIdentifier">
                    <xsl:element name="ProviderId"><xsl:value-of select="$providerId"/></xsl:element>
                    <xsl:element name="DateId"><xsl:value-of select="local:convertNcmDate($physPage/pub_date)"/></xsl:element>
                    <xsl:element name="NewsItemId"><xsl:value-of select="concat('package_', replace(replace(./name, ' ', '-'), '_', '-'), '_', ./obj_id)"/></xsl:element>
                    <xsl:element name="RevisionId">
                        <xsl:attribute name="PreviousRevision">0</xsl:attribute>
                        <xsl:attribute name="Update">N</xsl:attribute>
                        <xsl:text>1</xsl:text>
                    </xsl:element>
                    <xsl:element name="PublicIdentifier"><xsl:value-of select="local:crPkgRefId($physPage, .)"/></xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsManagement">
                <xsl:element name="NewsItemType">
                    <xsl:attribute name="FormalName">Package</xsl:attribute>
                </xsl:element>
                <xsl:element name="FirstCreated"><xsl:value-of select="local:convertNcmDate(./creation_ts)"/></xsl:element>
                <xsl:element name="ThisRevisionCreated"><xsl:value-of select="local:convertNcmDate(./modifier_ts)"/></xsl:element>
                <xsl:element name="Status">
                    <xsl:attribute name="FormalName"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
        <xsl:variable name="spId" select="./obj_id"/>
        <xsl:if test="local:hasTextualObjs($physPage, $spId) = 1">
            <!-- Text item -->
            <!-- create only one Text item per package -->
            <xsl:call-template name="createTextItem">
                <xsl:with-param name="textObjs" select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$spId and ncm-type-property/object-type/@id=1]"/>
                <xsl:with-param name="physPage" select="$physPage"/>
                <xsl:with-param name="storyPackage" select="."/>
            </xsl:call-template>        
        </xsl:if>
        <!-- non-Text items -->
        <xsl:apply-templates select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$spId and ncm-type-property/object-type/@id!=17 and ncm-type-property/object-type/@id!=1]" mode="item">
            <xsl:with-param name="physPage" select="$physPage"/>
            <xsl:with-param name="storyPackage" select="."/>
        </xsl:apply-templates>
    </xsl:template>
    
    <!-- standalone object item -->
    <xsl:template match="ncm-object" mode="standalone-item">
        <xsl:param name="physPage"/>
        <xsl:element name="NewsItem">
            <xsl:element name="Identification">
                <xsl:element name="NewsIdentifier">
                    <xsl:element name="ProviderId"><xsl:value-of select="$providerId"/></xsl:element>
                    <xsl:element name="DateId"><xsl:value-of select="local:convertNcmDate($physPage/pub_date)"/></xsl:element>
                    <xsl:element name="NewsItemId"><xsl:value-of select="concat('package_', replace(replace(./name, ' ', '-'), '_', '-'), '_', ./obj_id)"/></xsl:element>
                    <xsl:element name="RevisionId">
                        <xsl:attribute name="PreviousRevision">0</xsl:attribute>
                        <xsl:attribute name="Update">N</xsl:attribute>
                        <xsl:text>1</xsl:text>
                    </xsl:element>
                    <xsl:element name="PublicIdentifier"><xsl:value-of select="local:crPkgRefId($physPage, .)"/></xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsManagement">
                <xsl:element name="NewsItemType">
                    <xsl:attribute name="FormalName">Package</xsl:attribute>
                </xsl:element>
                <xsl:element name="FirstCreated"><xsl:value-of select="local:convertNcmDate(./creation_ts)"/></xsl:element>
                <xsl:element name="ThisRevisionCreated"><xsl:value-of select="local:convertNcmDate(./modifier_ts)"/></xsl:element>
                <xsl:element name="Status">
                    <xsl:attribute name="FormalName"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
        <xsl:variable name="objId" select="./obj_id"/>
        <!-- there are no Text components -->
        <!-- non-Text items -->
        <xsl:apply-templates select="." mode="item">
            <xsl:with-param name="physPage" select="$physPage"/>
            <xsl:with-param name="storyPackage" select="."/>
        </xsl:apply-templates>
    </xsl:template>    
    
    <xsl:template name="createTextItem">
        <xsl:param name="textObjs"/>
        <xsl:param name="physPage"/>
        <xsl:param name="storyPackage"/>
        <xsl:variable name="mainObj" select="local:getMainObj($textObjs, $storyPackage)"/>
        <xsl:variable name="pub" select="$physPage/edition/newspaper-level/level/@name"/>
        <xsl:element name="NewsItem">
            <xsl:element name="Identification">
                <xsl:element name="NewsIdentifier">
                    <xsl:element name="ProviderId"><xsl:value-of select="$providerId"/></xsl:element>
                    <xsl:element name="DateId"><xsl:value-of select="local:convertNcmDate($physPage/pub_date)"/></xsl:element>
                    <xsl:element name="NewsItemId"><xsl:value-of select="local:crObjId($mainObj)"/></xsl:element>
                    <xsl:element name="RevisionId">
                        <xsl:attribute name="PreviousRevision">0</xsl:attribute>
                        <xsl:attribute name="Update">N</xsl:attribute>
                        <xsl:text>1</xsl:text>
                    </xsl:element>
                    <xsl:element name="PublicIdentifier"><xsl:value-of select="local:crRefId($physPage, $mainObj)"/></xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsManagement">
                <xsl:element name="NewsItemType">
                    <xsl:attribute name="FormalName">Text</xsl:attribute>
                </xsl:element>
                <xsl:element name="FirstCreated"><xsl:value-of select="local:convertNcmDate($mainObj/creation_ts)"/></xsl:element>
                <xsl:element name="ThisRevisionCreated"><xsl:value-of select="local:convertNcmDate($mainObj/modifier_ts)"/></xsl:element>
                <xsl:element name="Status">
                    <xsl:attribute name="FormalName"/>
                </xsl:element>
                <xsl:element name="AssociatedWith">
                    <xsl:attribute name="NewsItem" select="local:crPkgRefId($physPage, $storyPackage)"/>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="Role">
                    <xsl:attribute name="FormalName">Text</xsl:attribute>
                </xsl:element>
                <xsl:element name="NewsLines">
                    <xsl:for-each select="$textObjs">
                        <xsl:element name="NewsLine">
                            <xsl:element name="NewsLineType">
                                <xsl:attribute name="FormalName">Text</xsl:attribute>
                            </xsl:element>
                            <xsl:element name="NewsLineText">
                                <xsl:choose>
                                    <xsl:when test="./convert-property[@format='Neutral']/story">
                                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                        </xsl:apply-templates>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text></xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:element>
                        </xsl:element>
                    </xsl:for-each>
                    <xsl:for-each select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=2]">
                        <xsl:choose>
                            <xsl:when test="local:isAdobe(./mediatype)=1"><!-- Adobe environment -->
                                <xsl:element name="NewsLine">
                                    <xsl:element name="NewsLineType">
                                        <xsl:attribute name="FormalName">Headline</xsl:attribute>
                                    </xsl:element>
                                    <xsl:element name="NewsLineText">
                                        <xsl:choose>
                                            <xsl:when test=".//convert-property[@format='Neutral']/story">
                                                <xsl:apply-templates select=".//convert-property[@format='Neutral']/story" mode="content">
                                                    <xsl:with-param name="mediatype" select="./mediatype"/>
                                                </xsl:apply-templates>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:text></xsl:text>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:element>
                                </xsl:element>                            
                            </xsl:when>
                            <xsl:otherwise><!-- Newsroom environment -->
                                <xsl:element name="NewsLine">
                                    <xsl:element name="NewsLineType">
                                        <xsl:attribute name="FormalName">Kicker</xsl:attribute>
                                    </xsl:element>
                                    <xsl:element name="NewsLineText">
                                        <xsl:choose>
                                            <xsl:when test=".//convert-property[@format='Neutral']/story">
                                                <xsl:choose>
                                                    <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']/par">
                                                        <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']" mode="content">
                                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                                        </xsl:apply-templates>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <xsl:text></xsl:text>
                                                    </xsl:otherwise>
                                                </xsl:choose>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:text></xsl:text>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:element>
                                </xsl:element>                        
                                <xsl:element name="NewsLine">
                                    <xsl:element name="NewsLineType">
                                        <xsl:attribute name="FormalName">Headline</xsl:attribute>
                                    </xsl:element>
                                    <xsl:element name="NewsLineText">
                                        <xsl:choose>
                                            <xsl:when test=".//convert-property[@format='Neutral']/story">
                                                <xsl:choose>
                                                    <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']/par">
                                                        <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Maintitle']" mode="content">
                                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                                        </xsl:apply-templates>
                                                    </xsl:when>
                                                    <xsl:when test=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']/par">
                                                        <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Supertitle']" mode="content">
                                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                                        </xsl:apply-templates>
                                                    </xsl:when>
                                                    <xsl:otherwise>
                                                        <xsl:text></xsl:text>
                                                    </xsl:otherwise>                        
                                                </xsl:choose>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:text></xsl:text>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:element>
                                </xsl:element>
                                <xsl:element name="NewsLine">
                                    <xsl:element name="NewsLineType">
                                        <xsl:attribute name="FormalName">Teaser</xsl:attribute>
                                    </xsl:element>
                                    <xsl:element name="NewsLineText">
                                        <xsl:choose>
                                            <xsl:when test=".//convert-property[@format='Neutral']/story">
                                                <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Teaser']" mode="content">
                                                    <xsl:with-param name="mediatype" select="./mediatype"/>
                                                </xsl:apply-templates>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:text></xsl:text>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:element>
                                </xsl:element>
                                <xsl:element name="NewsLine">
                                    <xsl:element name="NewsLineType">
                                        <xsl:attribute name="FormalName">Subtitle</xsl:attribute>
                                    </xsl:element>
                                    <xsl:element name="NewsLineText">
                                        <xsl:choose>
                                            <xsl:when test=".//convert-property[@format='Neutral']/story">
                                                <xsl:apply-templates select=".//convert-property[@format='Neutral']/story/headline/component[@name='Subtitle']" mode="content">
                                                    <xsl:with-param name="mediatype" select="./mediatype"/>
                                                </xsl:apply-templates>
                                            </xsl:when>
                                            <xsl:otherwise>
                                                <xsl:text></xsl:text>
                                            </xsl:otherwise>
                                        </xsl:choose>
                                    </xsl:element>
                                </xsl:element>                                           
                            </xsl:otherwise>
                        </xsl:choose>           
                    </xsl:for-each>
                    <xsl:for-each select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=3]">
                        <xsl:element name="NewsLine">
                            <xsl:element name="NewsLineType">
                                <xsl:attribute name="FormalName">Caption</xsl:attribute>
                            </xsl:element>
                            <xsl:element name="NewsLineText">
                                <xsl:choose>
                                    <xsl:when test="./convert-property[@format='Neutral']/story">
                                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                        </xsl:apply-templates>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text></xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:element>
                        </xsl:element>
                    </xsl:for-each>
                    <xsl:for-each select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=4]">
                        <xsl:element name="NewsLine">
                            <xsl:element name="NewsLineType">
                                <xsl:attribute name="FormalName">Header</xsl:attribute>
                            </xsl:element>
                            <xsl:element name="NewsLineText">
                                <xsl:choose>
                                    <xsl:when test="./convert-property[@format='Neutral']/story">
                                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                        </xsl:apply-templates>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text></xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:element>
                        </xsl:element>
                    </xsl:for-each>
                    <xsl:for-each select="$physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14]">
                        <xsl:element name="NewsLine">
                            <xsl:element name="NewsLineType">
                                <xsl:attribute name="FormalName">Summary</xsl:attribute>
                            </xsl:element>
                            <xsl:element name="NewsLineText">
                                <xsl:choose>
                                    <xsl:when test="./convert-property[@format='Neutral']/story">
                                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                                            <xsl:with-param name="mediatype" select="./mediatype"/>
                                        </xsl:apply-templates>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:text></xsl:text>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </xsl:element>
                        </xsl:element>
                    </xsl:for-each>
                </xsl:element>
                <xsl:element name="Metadata">
                    <xsl:element name="MetadataType">
                        <xsl:attribute name="FormalName">PublicationInfo</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Writer</xsl:attribute>
                        <xsl:attribute name="Value" select="$mainObj/creator/name"/>
                    </xsl:element>   
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">AssignmentID</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>   
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">EventID</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>                       
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Sub</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/SUB">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/SUB"/>
                            </xsl:when>
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/SUB, $storyPackage/extra-properties/OBJECT/SUB)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_SUB, $storyPackage/extra-properties/*[name()=$pub]/OBJ_SUB)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ReasonResub</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/REASONS_FOR_RESUBS">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/REASONS_FOR_RESUBS"/>
                            </xsl:when>                                                    
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/RESUB_REASON, $storyPackage/extra-properties/OBJECT/RESUB_REASON)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_RESUB_REASON, $storyPackage/extra-properties/*[name()=$pub]/OBJ_RESUB_REASON)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Resubs</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/NO_OF_RESUBS">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/NO_OF_RESUBS"/>
                            </xsl:when>                                                    
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/RESUB, $storyPackage/extra-properties/OBJECT/RESUB)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_RESUB, $storyPackage/extra-properties/*[name()=$pub]/OBJ_RESUB)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Checker</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/CHECKER">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/CHECKER"/>
                            </xsl:when>                            
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/CHECKER, $storyPackage/extra-properties/OBJECT/CHECKER)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_CHECKER, $storyPackage/extra-properties/*[name()=$pub]/OBJ_CHECKER)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">IsPrinted</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$physPage/is_printed='true'">
                                <xsl:attribute name="Value">1</xsl:attribute>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value">0</xsl:attribute>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ProductivityCode</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/PRODCODE, $storyPackage/extra-properties/OBJECT/PRODCODE)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_PRODCODE, $storyPackage/extra-properties/*[name()=$pub]/OBJ_PRODCODE)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:variable name="textCharCount" 
                            select="sum($textObjs/real_chars)"/>
                        <xsl:variable name="headerCharCount" 
                            select="sum($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=4]/real_chars)"/>
                        <xsl:variable name="summaryCharCount" 
                            select="sum($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14]/real_chars)"/>
                        <xsl:attribute name="FormalName">CharCount</xsl:attribute>
                        <xsl:attribute name="Value" select="$textCharCount + $headerCharCount + $summaryCharCount"/>
                    </xsl:element>                       
                    <xsl:element name="Property">
                        <xsl:variable name="textWordCount">
                            <xsl:choose>
                                <xsl:when test="local:isAdobe($textObjs[1]/mediatype)=1"><!-- Adobe environment -->
                                    <xsl:value-of select="sum(local:countWords($textObjs//convert-property[@format='Neutral']/story/par))"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="sum(local:countWords($textObjs//content-property[@type='NCMText']/formatted))"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:variable>
                        <xsl:variable name="headerWordCount">
                            <xsl:choose>
                                <xsl:when test="local:isAdobe(($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=4])[1]/mediatype)=1"><!-- Adobe environment -->
                                    <xsl:value-of select="sum(local:countWords($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=4]//convert-property[@format='Neutral']/story/par))"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="sum(local:countWords($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=4]//content-property[@type='NCMText']/formatted))"/>
                                </xsl:otherwise>
                            </xsl:choose>                            
                        </xsl:variable> 
                        <xsl:variable name="summaryWordCount">
                            <xsl:choose>
                                <xsl:when test="local:isAdobe(($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14])[1]/mediatype)=1"><!-- Adobe environment -->
                                    <xsl:value-of select="sum(local:countWords($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14]//convert-property[@format='Neutral']/story/par))"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="sum(local:countWords($physPage//ncm-layout[not(obj_id=preceding-sibling::ncm-layout/obj_id)]/objects-of-a-ncm-layout/ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14]//content-property[@type='NCMText']/formatted))"/>
                                </xsl:otherwise>
                            </xsl:choose>                             
                        </xsl:variable>
                        <xsl:attribute name="FormalName">WordCount</xsl:attribute>
                        <xsl:attribute name="Value" select="$textWordCount + $headerWordCount + $summaryWordCount"/>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Priority</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/PRIORITY">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/PRIORITY"/>
                            </xsl:when>                                                    
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/PRIORITY, $storyPackage/extra-properties/OBJECT/PRIORITY)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_PRIORITY, $storyPackage/extra-properties/*[name()=$pub]/OBJ_PRIORITY)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">StoryType</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/STORYTYPE, $storyPackage/extra-properties/OBJECT/STORYTYPE)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_STORYTYPE, $storyPackage/extra-properties/*[name()=$pub]/OBJ_STORYTYPE)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">CorrectedIds</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>                  
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Desk</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">NicaId</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">MasterId</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Size</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ImageCrop</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ImageTransformation</xsl:attribute>
                        <xsl:attribute name="Value" select="''"/>
                    </xsl:element>                       
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">WebCategory1</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/WEB_CAT_1">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/WEB_CAT_1"/>
                            </xsl:when>                                                    
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/WEBCAT1, $storyPackage/extra-properties/OBJECT/WEBCAT1)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_WEBCAT1, $storyPackage/extra-properties/*[name()=$pub]/OBJ_WEBCAT1)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">WebCategory2</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/WEB_CAT_2">
                                <xsl:attribute name="Value" select="$storyPackage/extra-properties/PRODUCTIVITY/WEB_CAT_2"/>
                            </xsl:when>                                                    
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/OBJECT/WEBCAT2, $storyPackage/extra-properties/OBJECT/WEBCAT2)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="($mainObj/extra-properties/*[name()=$pub]/OBJ_WEBCAT2, $storyPackage/extra-properties/*[name()=$pub]/OBJ_WEBCAT2)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">HermesObjectId</xsl:attribute>
                        <xsl:attribute name="Value" select="$mainObj/obj_id"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Source</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$textObjs//content-property[@type='NCMText']/formatted/uupscommand[@type='wc' and @value='1']">
                                <xsl:attribute name="Value" select="'AGENCY'"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="'SPH'"/>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Level</xsl:attribute>
                        <xsl:attribute name="Value" select="$mainObj/level/@path"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:variable name="copyrightValue">
                            <xsl:choose>
                                <xsl:when test="$textObjs//content-property[@type='NCMText']/formatted/uupscommand[@type='wc' and @value='1']">
                                    <xsl:value-of select="'NONSPH'"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:choose>
                                        <xsl:when test="$storyPackage/extra-properties/PRODUCTIVITY/COPYRIGHT">
                                            <xsl:value-of select="$storyPackage/extra-properties/PRODUCTIVITY/COPYRIGHT"/>
                                        </xsl:when>
                                        <xsl:when test="local:metadataNamingMode($pub)='1'">
                                            <xsl:value-of select="($storyPackage/extra-properties/OBJECT/COPYRIGHT, $copyright)[1]"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="($storyPackage/extra-properties/*[name()=$pub]/OBJ_COPYRIGHT, $copyright)[1]"/>
                                        </xsl:otherwise>
                                    </xsl:choose>                                    
                                </xsl:otherwise>
                            </xsl:choose>
                        </xsl:variable>
                        <xsl:attribute name="FormalName">Copyright</xsl:attribute>
                        <xsl:attribute name="Value" select="$copyrightValue"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ObjectType</xsl:attribute>
                        <xsl:attribute name="Value">Text</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">PubDate</xsl:attribute>
                        <xsl:attribute name="Value" select="local:convertNcmDate($physPage/pub_date)"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">CaptionId</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$physPage//ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=3]/obj_id" separator=","/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">SummaryId</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$physPage//ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=14]/obj_id" separator=","/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">HeadlineId</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$physPage//ncm-object[sp_id=$storyPackage/obj_id and ncm-type-property/object-type/@id=2]/obj_id" separator=","/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">NewsEvent</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$storyPackage/extra-properties/PRODUCTIVITY/NEWS_EVENT"/></xsl:attribute>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">CoAuthor</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$storyPackage/extra-properties/PRODUCTIVITY/COAUTHOR"/></xsl:attribute>
                    </xsl:element>                    
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">AdditionalReporting</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$storyPackage/extra-properties/PRODUCTIVITY/ADDITIONAL_REPORTING"/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Print</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="local:listPrintOptions($storyPackage/extra-properties/PRODUCTIVITY)"/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Online</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="local:listOnlineOptions($storyPackage/extra-properties/PRODUCTIVITY)"/></xsl:attribute>
                    </xsl:element>                                                                                                         
                </xsl:element>
                <xsl:element name="ContentItem">
                    <xsl:attribute name="Href" select="local:crFileName('Text', $physPage, $mainObj, 'txt')"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>
    
    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9]" mode="item">
        <xsl:param name="physPage"/>
        <xsl:param name="storyPackage"/>
        <xsl:variable name="spId" select="$storyPackage/obj_id"/>
        <xsl:variable name="objId" select="./obj_id"/>
        <xsl:variable name="reference" select="../../reference"/>
        <xsl:variable name="subreference" select="../../sub_reference" as="xs:integer"/>
        <xsl:variable name="pub" select="$physPage/edition/newspaper-level/level/@name"/>
        <xsl:variable name="typeName">
            <xsl:choose>
                <xsl:when test="ncm-type-property/object-type/@id=6">Image</xsl:when>
                <xsl:when test="ncm-type-property/object-type/@id=9">Graphic</xsl:when>
            </xsl:choose>
        </xsl:variable>
        <xsl:variable name="typeExt">
            <xsl:choose>
                <xsl:when test="ncm-type-property/object-type/@id=6">jpg</xsl:when>
                <xsl:when test="ncm-type-property/object-type/@id=9">pdf</xsl:when>
            </xsl:choose>
        </xsl:variable>          
        <xsl:element name="NewsItem">
            <xsl:element name="Identification">
                <xsl:element name="NewsIdentifier">
                    <xsl:element name="ProviderId"><xsl:value-of select="$providerId"/></xsl:element>
                    <xsl:element name="DateId"><xsl:value-of select="local:convertNcmDate($physPage/pub_date)"/></xsl:element>
                    <xsl:element name="NewsItemId"><xsl:value-of select="local:crObjId(.)"/></xsl:element>
                    <xsl:element name="RevisionId">
                        <xsl:attribute name="PreviousRevision">0</xsl:attribute>
                        <xsl:attribute name="Update">N</xsl:attribute>
                        <xsl:text>1</xsl:text>
                    </xsl:element>
                    <xsl:element name="PublicIdentifier"><xsl:value-of select="local:crRefId($physPage, .)"/></xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsManagement">
                <xsl:element name="NewsItemType">
                    <xsl:attribute name="FormalName"><xsl:value-of select="$typeName"/></xsl:attribute>
                </xsl:element>
                <xsl:element name="FirstCreated"><xsl:value-of select="local:convertNcmDate(./creation_ts)"/></xsl:element>
                <xsl:element name="ThisRevisionCreated"><xsl:value-of select="local:convertNcmDate(./modifier_ts)"/></xsl:element>
                <xsl:element name="Status">
                    <xsl:attribute name="FormalName"/>
                </xsl:element>
                <xsl:element name="AssociatedWith">
                    <xsl:attribute name="NewsItem" select="local:crPkgRefId($physPage, $storyPackage)"/>
                </xsl:element>
            </xsl:element>
            <xsl:element name="NewsComponent">
                <xsl:element name="Role">
                    <xsl:attribute name="FormalName"><xsl:value-of select="$typeName"/></xsl:attribute>
                </xsl:element>
                <xsl:comment select="concat('reference: ', $reference, '; sub_reference: ', $subreference)"/>
                <xsl:if test="local:getRefObjs($physPage, $spId, $objId, 3, $reference, $subreference) or local:getRefObjs($physPage, $spId, $objId, 16, $reference, $subreference)">
                    <xsl:element name="NewsLines">
                        <xsl:apply-templates select="local:getRefObjs($physPage, $spId, $objId, 3, $reference, $subreference)" mode="picture"/>
                        <xsl:apply-templates select="local:getRefObjs($physPage, $spId, $objId, 16, $reference, $subreference)" mode="picture"/>
                    </xsl:element>
                </xsl:if>
                <xsl:element name="Metadata">
                    <xsl:element name="MetadataType">
                        <xsl:attribute name="FormalName">PublicationInfo</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">IsPrinted</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="$physPage/is_printed='true'">
                                <xsl:attribute name="Value">1</xsl:attribute>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value">0</xsl:attribute>
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">NicaId</xsl:attribute>
                        <xsl:attribute name="Value">
                            <xsl:call-template name="getNicaId">
                                <xsl:with-param name="ncmObject" select="."/>
                            </xsl:call-template>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">MasterId</xsl:attribute>
                        <xsl:attribute name="Value">
                            <xsl:call-template name="getMasterId">
                                <xsl:with-param name="ncmObject" select="."/>
                            </xsl:call-template>
                        </xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Size</xsl:attribute>
                        <xsl:attribute name="Value" select="concat(./content-property/image-size/width, ' ', ./content-property/image-size/height)"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ImageCrop</xsl:attribute>
                        <xsl:attribute name="Value" select="concat(./content-property/crop-rect/@bottom, ' ', ./content-property/crop-rect/@left, ' ', ./content-property/crop-rect/@top, ' ', ./content-property/crop-rect/@right)"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">HermesObjectId</xsl:attribute>
                        <xsl:attribute name="Value" select="./obj_id"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Level</xsl:attribute>
                        <xsl:attribute name="Value" select="./level/@path"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">Copyright</xsl:attribute>
                        <xsl:choose>
                            <xsl:when test="local:metadataNamingMode($pub)='1'">
                                <xsl:attribute name="Value" select="(./extra-properties/OBJECT/COPYRIGHT, $storyPackage/extra-properties/OBJECT/COPYRIGHT)[1]"/>
                            </xsl:when>
                            <xsl:otherwise>
                                <xsl:attribute name="Value" select="(./extra-properties/*[name()=$pub]/OBJ_COPYRIGHT, $storyPackage/extra-properties/*[name()=$pub]/OBJ_COPYRIGHT)[1]"/>
                            </xsl:otherwise>
                        </xsl:choose>                        
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">ObjectType</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="$typeName"/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">PubDate</xsl:attribute>
                        <xsl:attribute name="Value" select="local:convertNcmDate($physPage/pub_date)"/>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">CaptionId</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="local:getRefObjIds($physPage, $spId, $objId, 3, $reference, $subreference)" separator=","/></xsl:attribute>
                    </xsl:element>
                    <xsl:element name="Property">
                        <xsl:attribute name="FormalName">CreditId</xsl:attribute>
                        <xsl:attribute name="Value"><xsl:value-of select="local:getRefObjIds($physPage, $spId, $objId, 16, $reference, $subreference)" separator=","/></xsl:attribute>
                    </xsl:element>
                </xsl:element>
                <xsl:element name="ContentItem">
                    <xsl:attribute name="Href" select="local:crFileName($typeName, $physPage, ., $typeExt)"/>
                </xsl:element>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=3]" mode="picture">
        <xsl:comment select="concat('CaptionId: ', ./obj_id, '; reference: ', ../../reference, '; sub_reference: ', ../../sub_reference)"/>
        <xsl:element name="NewsLine">
            <xsl:element name="NewsLineType">
                <xsl:attribute name="FormalName">Caption</xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsLineText">
                <xsl:choose>
                    <xsl:when test="./convert-property[@format='Neutral']/story">
                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                            <xsl:with-param name="mediatype" select="./mediatype"/>
                        </xsl:apply-templates>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:text></xsl:text>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="ncm-object[ncm-type-property/object-type/@id=16]" mode="picture">
        <xsl:element name="NewsLine">
            <xsl:element name="NewsLineType">
                <xsl:attribute name="FormalName">Credit</xsl:attribute>
            </xsl:element>
            <xsl:element name="NewsLineText">
                <xsl:choose>
                    <xsl:when test="./convert-property[@format='Neutral']/story">
                        <xsl:apply-templates select="./convert-property[@format='Neutral']/story" mode="content">
                            <xsl:with-param name="mediatype" select="./mediatype"/>
                        </xsl:apply-templates>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:text></xsl:text>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:element>
        </xsl:element>
    </xsl:template>

    <xsl:template match="story|component" mode="content">
        <xsl:param name="mediatype"/>
        <!-- loop through all par nodes -->
	<xsl:for-each select=".//par">
            <xsl:variable name="tag" select="local:handleMergeCopy(@name)"/>
            <xsl:apply-templates select="text()|char" mode="content">
                <xsl:with-param name="mediatype" select="$mediatype"/>
                <xsl:with-param name="tag" select="$tag"/>
            </xsl:apply-templates>
            <xsl:if test="not(local:isAdobe($mediatype)=1)"><!-- Newsroom environment -->
                <xsl:if test="position() != last()">
                    <xsl:text>&lt;br/&gt;</xsl:text><!-- separate paragraphs with br -->
                </xsl:if>
            </xsl:if>
	</xsl:for-each>
    </xsl:template>
     
    <xsl:template match="char" mode="content">
        <xsl:param name="mediatype"/>
        <xsl:variable name="tag" 
            select="if (exists(@override-by)) then local:handleMergeCopy(@override-by) else local:handleMergeCopy(@name)"/>
        <xsl:choose>
            <xsl:when test="$tag='note'">
                <!-- print nothing: remove notice mode text -->
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="fontId" select="@font-id"/>
                <xsl:variable name="orig" select="text()"/>     
                <xsl:variable name="replacement"
                    select="$specialCharMapDoc/map[@tag=$tag and @font-id=$fontId and @orig-text=$orig]"/>
                <xsl:choose>
                    <xsl:when test="$replacement='clear'">
                        <xsl:value-of select="''"/><!-- remove text -->
                    </xsl:when>
                    <xsl:when test="$replacement">
                        <xsl:value-of select="$replacement"/><!-- replacement exists, replace string -->
                    </xsl:when>                    
                    <xsl:otherwise>
                        <xsl:apply-templates select="text()" mode="content"><!-- just output orig string -->
                            <xsl:with-param name="mediatype" select="$mediatype"/>
                            <xsl:with-param name="tag" select="$tag"/>                    
                        </xsl:apply-templates>
                        <!-- <xsl:value-of select="concat('[/', $tag, ']')"/> --><!-- close char tag -->
                    </xsl:otherwise>                    
                </xsl:choose>                                       
            </xsl:otherwise>
	</xsl:choose>
    </xsl:template>
       
    <xsl:template match="text()" mode="content">
        <xsl:param name="mediatype"/>
        <xsl:param name="tag"/>
        <xsl:value-of select="concat('[', $tag, ']')"/><!-- open tag -->          
        <!-- don't normalize space -->
        <!-- replace reserved chars -->
        <!-- remove &#8232; (soft return) --> 
        <!-- replace any newlines with <br/> -->
        <xsl:value-of 
            select="replace(
                        replace(
                            local:replaceReservedChars(.),
                        '&#8232;', ''),
                    '&#x0A;', '&lt;br/&gt;')" 
            disable-output-escaping="yes"/>
    </xsl:template>

    <xsl:template match="text()" mode="component"/>
    
    <xsl:template match="text()" mode="item"/>
    
    <xsl:template match="text()"/>
    
    <xsl:template name="getNicaId">
        <xsl:param name="ncmObject"/>
        <xsl:if test="$ncmObject/obj_comment">
            <xsl:analyze-string select="$ncmObject/obj_comment" regex="(&lt;NICA:.*?:.*?&gt;)">
                <xsl:matching-substring>
                    <xsl:value-of select="tokenize(replace(replace(regex-group(1), '&lt;', ''), '&gt;', ''), ':')[3]"/>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:if>
    </xsl:template>

    <xsl:template name="getMasterId">
        <xsl:param name="ncmObject"/>
        <xsl:if test="$ncmObject/obj_comment">
            <xsl:analyze-string select="$ncmObject/obj_comment" regex="(&lt;NICA:.*?:.*?&gt;)">
                <xsl:matching-substring>
                    <xsl:value-of select="tokenize(replace(replace(regex-group(1), '&lt;', ''), '&gt;', ''), ':')[2]"/>
                </xsl:matching-substring>
            </xsl:analyze-string>
        </xsl:if>
    </xsl:template>

    <xsl:function name="local:getRefObjs">
        <xsl:param name="physPage"/>
        <xsl:param name="spId"/>
        <xsl:param name="objId"/>
        <xsl:param name="refType"/>
        <xsl:param name="reference"/>
        <xsl:param name="subreference"/>
        <xsl:choose>
            <xsl:when test="$physPage//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType and relation_obj_id=$objId]">
                <xsl:sequence select="$physPage//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType and relation_obj_id=$objId]"/>
            </xsl:when>
            <xsl:when test="$physPage//ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]">
                <xsl:sequence select="$physPage//ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="$physPage//ncm-layout[local:hasReference($physPage, $spId, reference, xs:integer(sub_reference)) eq 0]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:getRefObjIds">
        <xsl:param name="physPage"/>
        <xsl:param name="spId"/>
        <xsl:param name="objId"/>
        <xsl:param name="refType"/>
        <xsl:param name="reference"/>
        <xsl:param name="subreference"/>
        <xsl:choose>
            <xsl:when test="$physPage//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType and relation_obj_id=$objId]">
                <xsl:sequence select="$physPage//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType and relation_obj_id=$objId]/obj_id"/>
            </xsl:when>
            <xsl:when test="$physPage//ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]">
                <xsl:sequence select="$physPage//ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]/obj_id"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="$physPage//ncm-layout[local:hasReference($physPage, $spId, reference, xs:integer(sub_reference)) eq 0]//ncm-object[sp_id=$spId and xs:integer(ncm-type-property/object-type/@id) eq $refType]/obj_id"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:hasReference">
        <xsl:param name="physPage"/>
        <xsl:param name="spId"/>
        <xsl:param name="reference"/>
        <xsl:param name="subreference"/>
        <xsl:choose>
            <xsl:when test="$physPage//ncm-layout[reference=$reference and xs:integer(sub_reference) ne 0 and xs:integer(sub_reference) eq $subreference]//ncm-object[sp_id=$spId and (ncm-type-property/object-type/@id=6 or ncm-type-property/object-type/@id=9)]">
                <xsl:sequence select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="0"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:crFileName">
        <xsl:param name="prefix"/>
        <xsl:param name="physPage"/>
        <xsl:param name="ncmObject"/>
        <xsl:param name="extension"/>
        <xsl:sequence select="concat($prefix, '_', $ncmObject/obj_id, '_', $physPage//ncm-logical-page[layouts-in-ncm-logical-page/ncm-layout/objects-of-a-ncm-layout/ncm-object[obj_id=$ncmObject/obj_id]]/page_id, '.', $extension)"/>
    </xsl:function>

    <xsl:function name="local:crPkgRefId">
        <xsl:param name="physPage"/>
        <xsl:param name="ncmObject"/>
        <xsl:sequence select="concat($itemRefPrefix, local:convertNcmDate($physPage/pub_date), ':package_', replace(replace($ncmObject/name, ' ', '-'), '_', '-'), '_', $ncmObject/obj_id, ':1')"/>
    </xsl:function>    
    
    <xsl:function name="local:crRefId">
        <xsl:param name="physPage"/>
        <xsl:param name="ncmObject"/>
        <!--
        <xsl:choose>
            <xsl:when test="$ncmObject/ncm-type-property/object-type/@id=17">
                <xsl:sequence select="concat($itemRefPrefix, local:convertNcmDate($physPage/pub_date), ':package_', replace(replace($ncmObject/name, ' ', '-'), '_', '-'), '_', $ncmObject/obj_id, ':1')"/>        
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="concat($itemRefPrefix, local:convertNcmDate($physPage/pub_date), ':', local:crObjId($ncmObject), ':1')"/>        
            </xsl:otherwise>
        </xsl:choose>
        -->
        <xsl:sequence select="concat($itemRefPrefix, local:convertNcmDate($physPage/pub_date), ':', local:crObjId($ncmObject), ':1')"/>
    </xsl:function>
    
    <xsl:function name="local:crObjId">
        <xsl:param name="ncmObject"/>
        <xsl:sequence select="concat('object-', $ncmObject/obj_id, '-', $ncmObject/ncm-type-property/object-type/@id)"/>
    </xsl:function>
    
    <xsl:function name="local:convertNcmDate">
        <xsl:param name="ncmDateStr"/>
        <xsl:variable name="dateParts" select="tokenize(replace($ncmDateStr, ',', ''), '\s+')"/>
        <xsl:choose>
            <xsl:when test="string-length($dateParts[2])=1">
                <xsl:sequence select="concat($dateParts[3], local:shortMonthToNum($dateParts[1]), '0', $dateParts[2])"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="concat($dateParts[3], local:shortMonthToNum($dateParts[1]), $dateParts[2])"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:crPageNum">
        <xsl:param name="section"/>
        <xsl:param name="pageNum"/>
        <xsl:param name="pageNum1"/>
        <xsl:choose>
            <xsl:when test="string-length($pageNum1) gt 0">
                <xsl:choose>
                    <xsl:when test="$pageNum = $pageNum1">
                        <xsl:value-of select="concat($section, $pageNum)"/>
                    </xsl:when>
                    <xsl:when test="$pageNum gt $pageNum1">
                        <xsl:value-of select="concat($section, $pageNum1, ',', $section, $pageNum)"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="concat($section, $pageNum, ',', $section, $pageNum1)"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="concat($section, $pageNum)"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>    
    
    <xsl:function name="local:shortMonthToNum">
        <xsl:param name="shortMonthStr"/>
        <xsl:choose>
            <xsl:when test="$shortMonthStr='Jan'">
                <xsl:sequence select="'01'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Feb'">
                <xsl:sequence select="'02'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Mar'">
                <xsl:sequence select="'03'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Apr'">
                <xsl:sequence select="'04'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='May'">
                <xsl:sequence select="'05'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Jun'">
                <xsl:sequence select="'06'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Jul'">
                <xsl:sequence select="'07'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Aug'">
                <xsl:sequence select="'08'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Sep'">
                <xsl:sequence select="'09'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Oct'">
                <xsl:sequence select="'10'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Nov'">
                <xsl:sequence select="'11'"/>
            </xsl:when>
            <xsl:when test="$shortMonthStr='Dec'">
                <xsl:sequence select="'12'"/>
            </xsl:when>
        </xsl:choose>
    </xsl:function>

    <xsl:function name="local:countWords">
        <xsl:param name="texts"/>
        <xsl:for-each select="$texts">
            <xsl:variable name="text" select="."/>
            <xsl:value-of select="count(tokenize($text, '\W+')[. != ''])"/>
        </xsl:for-each>
    </xsl:function>

    <xsl:function name="local:exportStandaloneObjType">
        <xsl:param name="objType"/>
        <!-- export standalone images and graphics -->
        <xsl:choose>
            <xsl:when test="$objType=6 or $objType=9">
                <xsl:sequence select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="0"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <xsl:function name="local:hasTextualObjs">
        <xsl:param name="physPage"/>
        <xsl:param name="spId"/>
        <!-- count: text, headline, caption, header, summary objects -->
        <xsl:variable name="textualObjCount"
            select="count($physPage//ncm-object[sp_id=$spId and 
                (ncm-type-property/object-type/@id=1 or ncm-type-property/object-type/@id=2 or
                 ncm-type-property/object-type/@id=3 or ncm-type-property/object-type/@id=4 or
                 ncm-type-property/object-type/@id=14)])"/>
        <xsl:choose>
            <xsl:when test="$textualObjCount gt 0">
                <xsl:sequence select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="0"/>
            </xsl:otherwise>
        </xsl:choose>        
    </xsl:function>
    
    <xsl:function name="local:getMainObj">
        <xsl:param name="textObjs"/>
        <xsl:param name="storyPackage"/>
        <!-- get first text object, otherwise the package -->
        <xsl:choose>
            <xsl:when test="count($textObjs) gt 0">
                <xsl:sequence select="$textObjs[1]"/><!-- get the first text object -->
            </xsl:when>
            <xsl:otherwise>
                <xsl:sequence select="$storyPackage"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:replaceReservedChars">
        <xsl:param name="text"/>
        <!-- square brackets mark newsroom tags. replace them with markers -->
        <xsl:value-of 
            select="replace(replace($text, '\[', '__OPEN_SQR_BRACKET__'), '\]', '__CLOSE_SQR_BRACKET__')" 
            disable-output-escaping="yes"/>
    </xsl:function>    

    <xsl:function name="local:handleMergeCopy">
        <xsl:param name="tag"/>
        <!-- remove '_MCn' suffix, e.g. 'CAPTION_MC1' becomes 'CAPTION' -->
        <!-- remove square brackets in tag names, e.g. [No paragraph style] -->
        <xsl:value-of 
            select="replace(
                        replace(
                            replace($tag, '_MC\d+$', ''),
                        '\]', ''),
                    '\[', '')"/>
    </xsl:function>
    
    <xsl:function name="local:isAdobe">
        <xsl:param name="mediatype"/>
        <xsl:choose>
            <xsl:when test="contains(lower-case($mediatype), 'incopy') or contains(lower-case($mediatype), 'indesign')">
                <xsl:value-of select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:metadataNamingMode">
        <xsl:param name="pub"/>
        <!-- note: naming convention for metadata are different for some pubs -->
        <xsl:choose>
            <xsl:when test="$pub='ST' or $pub='MY' or $pub='TABL'">
                <xsl:value-of select="1"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="0"/>
            </xsl:otherwise>            
        </xsl:choose>
    </xsl:function>
    
    <xsl:function name="local:listPrintOptions">
        <xsl:param name="meta"/>
        
        <xsl:variable name="str">
            <xsl:if test="$meta/PRINT_DIARY='true'">,Diary/Pick-up</xsl:if>
            <xsl:if test="$meta/PRINT_OWN='true'">,Own/Exclusive</xsl:if>
            <xsl:if test="$meta/PRINT_COMMENTARY='true'">,Commentary/Analysis</xsl:if>
            <xsl:if test="$meta/PRINT_FEATURE='true'">,Feature</xsl:if>
            <xsl:if test="$meta/PRINT_SIDEBAR='true'">,Sidebar</xsl:if>
            <xsl:if test="$meta/PRINT_INFOGRAPHICS='true'">,Infographics</xsl:if>
            <xsl:if test="$meta/PRINT_WIRE='true'">,Wire</xsl:if>
            <xsl:if test="$meta/PRINT_CONTRIBUTION='true'">,Contribution</xsl:if>
        </xsl:variable>
        
        <xsl:choose>
            <xsl:when test="substring($str, 1, 1)=','">
                <xsl:value-of select="substring($str, 2)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$str"/>
            </xsl:otherwise>
        </xsl:choose>        
    </xsl:function>

    <xsl:function name="local:listOnlineOptions">
        <xsl:param name="meta"/>
        
        <xsl:variable name="str">
            <xsl:if test="$meta/ONLINE_DIARY='true'">,Diary</xsl:if>
            <xsl:if test="$meta/ONLINE_OWN='true'">,Own/Exclusive</xsl:if>
            <xsl:if test="$meta/ONLINE_TWEETS='true'">,Tweets</xsl:if>
            <xsl:if test="$meta/ONLINE_FIRST_PHOTOS='true'">,First Photos</xsl:if>
            <xsl:if test="$meta/ONLINE_FIRST_VIDEOS='true'">,First Videos</xsl:if>
            <xsl:if test="$meta/ONLINE_VIDEO_PACKAGE='true'">,Video Package</xsl:if>
            <xsl:if test="$meta/ONLINE_BLOG='true'">,Blog/Communities</xsl:if>
        </xsl:variable>
        
        <xsl:choose>
            <xsl:when test="substring($str, 1, 1)=','">
                <xsl:value-of select="substring($str, 2)"/>
            </xsl:when>
            <xsl:otherwise>
                <xsl:value-of select="$str"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>
        
</xsl:stylesheet>
