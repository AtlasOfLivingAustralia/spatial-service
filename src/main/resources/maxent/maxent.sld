<?xml version="1.0" encoding="ISO-8859-1"?>
<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
  xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>alastyles</Name>
    <UserStyle>
      <Name>alastyles</Name>
      <Title>ALA MaxEnt distribution</Title>
      <FeatureTypeStyle>
        <Rule>
          <RasterSymbolizer>
            <ColorMap type="intervals" extended="true">
              <ColorMapEntry color="#FFFFFF" quantity="0.0000" opacity="0.0" label="x &lt; 0"/>
              <ColorMapEntry color="#CCFF00" quantity="0.0001" opacity="1" label="0 &lt;= x &lt; 0.0001"/>
              <ColorMapEntry color="#CCCC00" quantity="0.2" opacity="1" label="0.0001 &lt;= x &lt; 0.2"/>
              <ColorMapEntry color="#CC9900" quantity="0.4" opacity="1" label="0.2 &lt;= x &lt; 0.4"/>
              <ColorMapEntry color="#CC6600" quantity="0.6" opacity="1" label="0.4 &lt;= x &lt; 0.6"/>
              <ColorMapEntry color="#CC3300" quantity="0.8" opacity="1" label="0.6 &lt;= x &lt; 0.8"/>
              <ColorMapEntry color="#0000FF" quantity="1.0" opacity="1" label="0.8 &lt;= x &lt;= 1"/>
            </ColorMap>
          </RasterSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
