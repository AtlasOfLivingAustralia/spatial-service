<?xml version="1.0" encoding="UTF-8"?>
<StyledLayerDescriptor xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="1.0.0" xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
   <NamedLayer>
      <Name>Points</Name>
      <UserStyle>
         <Title>Stardard marker</Title>
         <FeatureTypeStyle>
            <Rule>
               <Title>dark orange point</Title>
               <PointSymbolizer>
                  <Graphic>
                     <ExternalGraphic>
                        <OnlineResource xlink:type="simple" xlink:href="https://spatial.ala.org.au/geoserver/styles/marker.png" />
                        <Format>image/png</Format>
                     </ExternalGraphic>
                     <Size>36</Size>
                  </Graphic>
               </PointSymbolizer>
               <TextSymbolizer>
                  <Label>
                     <ogc:Function name="env">
                        <ogc:PropertyName>name</ogc:PropertyName>
                     </ogc:Function>
                  </Label>
                  <Font>
                     <CssParameter name="font-family">Serif</CssParameter>
                     <CssParameter name="font-size">12</CssParameter>
                     <CssParameter name="font-style">normal</CssParameter>
                  </Font>
                  <LabelPlacement>
                     <PointPlacement>
                        <AnchorPoint>
                           <AnchorPointX>0.5</AnchorPointX>
                           <AnchorPointY>0</AnchorPointY>
                        </AnchorPoint>
                        <Displacement>
                           <DisplacementX>0</DisplacementX>
                           <DisplacementY>20</DisplacementY>
                        </Displacement>
                     </PointPlacement>
                  </LabelPlacement>
                  <Fill>
                     <CssParameter name="fill">#.000000</CssParameter>
                  </Fill>
                  <VendorOption name="partials">true</VendorOption>
               </TextSymbolizer>
            </Rule>
         </FeatureTypeStyle>
      </UserStyle>
   </NamedLayer>
</StyledLayerDescriptor>