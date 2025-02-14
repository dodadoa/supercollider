Plot {
	classvar <initGuiSkin;

	var <>plotter, <value;
	var <bounds, <plotBounds,<>drawGrid;

	var <spec, <domainSpec;
	var <font, <fontColor, <gridColorX, <gridColorY, <plotColor, <>backgroundColor, <plotMode;
	var <gridOnX = true, <gridOnY = true, <labelX, <labelY, labelXisUnits = false, labelYisUnits = false;
	var txtPad = 2;

	var valueCache, resolution;

	*initClass {
		if(Platform.hasQt.not) { ^nil; };	// skip init on Qt-less builds

		initGuiSkin = {
			var palette = QtGUI.palette;
			var hl = palette.highlight;
			var base = palette.base;
			var baseText = palette.baseText;
			var butt = palette.button;
			var gridCol = palette.window;
			var labelCol = butt.blend(baseText, 0.6);

			GUI.skins.put(\plot, (
				gridColorX: gridCol,
				gridColorY: gridCol,
				fontColor: labelCol,
				plotColor: [baseText.blend(hl, 0.5)], // should be an array for multichannel support
				background: base,
				gridLinePattern: nil,
				gridLineSmoothing: false,
				labelX: nil,
				labelY: nil,
				expertMode: false,
				gridFont: Font( Font.defaultSansFace, 9 )
			));
		};
		initGuiSkin.value;
		StartUp.add(this);
	}

	*doOnStartUp {
		initGuiSkin.value;
	}

	*new { |plotter|
		^super.newCopyArgs(plotter).init
	}

	init {
		var fontName;
		var skin = GUI.skins.at(\plot);

		drawGrid = DrawGrid(bounds ? Rect(0,0,1,1),nil,nil);
		drawGrid.x.labelOffset = Point(0,4);
		drawGrid.y.labelOffset = Point(-10,0);
		skin.use {
			font = ~gridFont ?? { Font( Font.defaultSansFace, 9 ) };
			this.gridColorX = ~gridColorX;
			this.gridColorY = ~gridColorY;
			plotColor = ~plotColor;
			this.fontColor = ~fontColor;
			backgroundColor = ~background;
			this.gridLineSmoothing = ~gridLineSmoothing;
			this.gridLinePattern = ~gridLinePattern !? {~gridLinePattern.as(FloatArray)};
			labelX = ~labelX;
			labelY = ~labelY;
		};
		resolution = plotter.resolution;
	}

	bounds_ { |viewRect|
		var gridRect;
		var xtkLabels, ytkLabels, ytkLabelSize, xtkLabelSize, xLabelGridOffset, yLabelGridOffset;
		var xLabelSize, yLabelSize, maxWidthWithLabel, maxHeightWithLabel;
		var hshift, vshift, hinset, vinset, lmargin, rmargin, totalVSpace;
		var minGridMargin = 4;

		bounds = viewRect;
		valueCache = nil;

		// find the size of the largest tick labels
		#xtkLabelSize, xtkLabels = this.prGetTickLabelSize(drawGrid.x);
		#ytkLabelSize, ytkLabels = this.prGetTickLabelSize(drawGrid.y);
		xLabelSize = labelX.notNil.if({ labelX.bounds(font) }, { Rect(0,0,0,0) });
		yLabelSize = labelY.notNil.if({ labelY.bounds(font) }, { Rect(0,0,0,0) });

		// zeroing out drawGrids' labelOffsets disables labels, see DrawGridX/Y:commands
		xLabelGridOffset = yLabelGridOffset = Point(0, 0);

		// labels disappear below these size thresholds
		maxWidthWithLabel = ytkLabelSize.width * 4;
		maxHeightWithLabel = xtkLabelSize.height * 4;

		hshift = vshift = hinset = vinset = 0;

		// x axis label spacing
		if( viewRect.height >= maxHeightWithLabel
			and: { viewRect.width >= (xtkLabelSize.width*3)
				and: { xtkLabels.notNil }
		}) {
			totalVSpace = txtPad + xtkLabelSize.height + txtPad + xLabelSize.height + txtPad;
			hinset = xtkLabelSize.width/2 + txtPad;
			vinset = totalVSpace * (2/3);     // inset more on sides with labels
			vshift = totalVSpace * (1/3).neg;
			xLabelGridOffset = Point(xtkLabelSize.width, xtkLabelSize.height);
		};

		// y axis label spacing
		if(viewRect.width >= maxWidthWithLabel and: { ytkLabels.notNil }) {
			lmargin = txtPad + ytkLabelSize.width + txtPad;
			if (yLabelSize.height > 0) {
				lmargin = lmargin + yLabelSize.height + txtPad };
			if (hinset > 0) { // x labels present
				rmargin = hinset;
				lmargin = max(rmargin, lmargin);
				hinset = (rmargin + lmargin) / 2;
				hshift = max(0, lmargin - hinset);
			} { // only y labels present
				hinset = (lmargin + minGridMargin) / 2;
				hshift = lmargin - hinset;
			};
			yLabelGridOffset = Point(lmargin, ytkLabelSize.height);
		};

		hinset = max(hinset, minGridMargin);
		vinset = max(vinset, minGridMargin);
		drawGrid.x.labelOffset = xLabelGridOffset;
		drawGrid.y.labelOffset = yLabelGridOffset;
		gridRect = viewRect.insetBy(hinset, vinset) + [hshift, vshift, 0, 0];
		drawGrid.bounds = gridRect;

		// plotBounds are the bounds of grid/data excluding label margin (= drawGrid.bounds)
		plotBounds = gridRect;
	}

	value_ { |array|
		value = array;
		valueCache = nil;
	}
	spec_ { |sp|
		spec = sp;
		if(gridOnY and: spec.notNil, {
			drawGrid.vertGrid = spec.grid;

			if (spec.units.isEmpty, {
				// remove label if previously set by spec units
				if (labelYisUnits, {
					labelY = nil;
					labelYisUnits = false;
				});
			},{ // add new or overwrite previous unit label
				if(labelY.isNil or: labelYisUnits) {
					labelY = spec.units;
					labelYisUnits = true;
				}
			})
		},{
			drawGrid.vertGrid = nil
		})
	}
	domainSpec_ { |sp|
		domainSpec = sp;
		if(gridOnX and: domainSpec.notNil,{
			drawGrid.horzGrid = domainSpec.grid;

			if (domainSpec.units.isEmpty, { // see comments in spec_
				if (labelXisUnits, {
					labelX = nil;
					labelXisUnits = false;
				});
			},{
				if(labelX.isNil or: labelXisUnits) {
					labelX = domainSpec.units;
					labelXisUnits = true;
				}
			})
		},{
			drawGrid.horzGrid = nil
		})
	}
	plotColor_ { |c|
		plotColor = c.as(Array);
	}
	gridColorX_ { |c|
		drawGrid.x.gridColor = c;
		gridColorX = c;
	}
	gridColorY_ { |c|
		drawGrid.y.gridColor = c;
		gridColorY = c;
	}
	plotMode_ { |m|
		plotMode = m.asArray
	}
	font_ { |f|
		font = f;
		drawGrid.font = f;
	}
	fontColor_ { |c|
		fontColor = c;
		drawGrid.fontColor = c;
	}
	labelX_ { |string|
		labelX = string;
		labelXisUnits = false;
	}
	labelY_ { |string|
		labelY = string;
		labelYisUnits = false;
	}
	gridLineSmoothing_ { |bool|
		drawGrid.smoothing = bool;
	}
	gridLinePattern_ { |pattern|
		drawGrid.linePattern = pattern;
	}
	gridOnX_ { |bool|
		gridOnX = bool;
		drawGrid.horzGrid = if(gridOnX,{domainSpec.grid},{nil});
	}
	gridOnY_ { |bool|
		gridOnY= bool;
		drawGrid.vertGrid = if(gridOnY,{spec.grid},{nil});
	}

	draw {
		this.drawBackground;
		drawGrid.draw;
		this.drawLabels;
		this.drawData;
		plotter.drawFunc.value(this); // additional elements
	}

	drawBackground {
		Pen.addRect(bounds);
		Pen.fillColor = backgroundColor;
		Pen.fill;
	}

	drawLabels {
		var lbounds;
		if(labelX.notNil and: { gridOnX and: { drawGrid.x.labelOffset.y > 0 }}) {
			try {
				lbounds = labelX.bounds(font);
				Pen.stringCenteredIn(labelX,
					lbounds.center_(
						plotBounds.center.x @ (plotBounds.bottom + (lbounds.height * 1.5) + (2 * txtPad))
					), font, fontColor
				);
			}
		};
		if(labelY.notNil and: { gridOnY and: { drawGrid.y.labelOffset.x > 0 }}) {
			try {
				lbounds = labelY.bounds(font);
				Pen.push;
				Pen.translate(
					plotBounds.left - drawGrid.y.labelOffset.x + txtPad + (lbounds.height/2),
					plotBounds.center.y);
				Pen.rotateDeg(-90);
				Pen.stringCenteredIn(labelY, lbounds.center_(0@0), font, fontColor);
				Pen.pop;
			}
		};
	}

	domainCoordinates { |size|
		var range, vals;

		vals = if (plotter.domain.notNil) {
			domainSpec.unmap(plotter.domain);
		} {
			range = domainSpec.range;
			if (range == 0.0 or: { size == 1 }) {
				0.5.dup(size) // put the values in the middle of the plot
			} {
				domainSpec.unmap(
					Array.interpolation(size, domainSpec.minval, domainSpec.maxval)
				);
			}
		};

		^plotBounds.left + (vals * plotBounds.width);
	}

	dataCoordinates {
		var val = spec.warp.unmap(this.prResampValues);
		^plotBounds.bottom - (val * plotBounds.height); // measures from top left (may be arrays)
	}

	drawData {
		var ycoord = this.dataCoordinates;
		var xcoord = this.domainCoordinates(ycoord.size);

		Pen.use {
			Pen.width = 1.0;
			Pen.joinStyle = 1;
			Pen.addRect(plotBounds);
			Pen.clip; // clip curve to bounds.

			if(ycoord.at(0).isSequenceableCollection) { // multi channel expansion when superposed
				ycoord.flop.do { |y, i|
					var mode = plotMode.wrapAt(i);
					Pen.beginPath;
					this.perform(mode, xcoord, y);
					if (this.needsPenFill(mode)) {
						Pen.fillColor = plotColor.wrapAt(i);
						Pen.fill
					} {
						Pen.strokeColor = plotColor.wrapAt(i);
						Pen.stroke
					}
				}
			} {
				Pen.beginPath;
				Pen.strokeColor = plotColor.at(0);
				Pen.fillColor= plotColor.at(0);
				this.perform(plotMode.at(0), xcoord, ycoord);
				if (this.needsPenFill(plotMode.at(0))) {
					Pen.fill
				} {
					Pen.stroke
				}
			};
			Pen.joinStyle = 0;
		};
	}

	// modes

	linear { |x, y|
		Pen.moveTo(x.first @ y.first);
		y.size.do { |i|
			Pen.lineTo(x[i] @ y[i]);
		}
	}

	points { |x, y|
		var size = min(bounds.width / value.size * 0.25, 4);
		y.size.do { |i|
			Pen.addArc(x[i] @ y[i], 0.5, 0, 2pi);
			if(size > 2) { Pen.addArc(x[i] @ y[i], size, 0, 2pi); };
		}
	}

	plines { |x, y|
		var size = min(bounds.width / value.size * 0.25, 3);
		Pen.moveTo(x.first @ y.first);
		y.size.do { |i|
			var p = x[i] @ y[i];
			Pen.lineTo(p);
			Pen.addArc(p, size, 0, 2pi);
			Pen.moveTo(p);
		}
	}

	levels { |x, y|
		Pen.smoothing_(false);
		y.size.do { |i|
			Pen.moveTo(x[i] @ y[i]);
			Pen.lineTo(x[i + 1] ?? { plotBounds.right } @ y[i]);
		}
	}

	steps { |x, y|
		Pen.smoothing_(false);
		Pen.moveTo(x.first @ y.first);
		y.size.do { |i|
			Pen.lineTo(x[i] @ y[i]);
			Pen.lineTo(x[i + 1] ?? { plotBounds.right } @ y[i]);
		}
	}

	bars { |x, y |
		Pen.smoothing_(false);
		y.size.do { |i|
			var p = x[i] @ y[i];
			var nextx = x[i + 1] ?? {plotBounds.right};
			var centery = 0.linlin(this.spec.minval, this.spec.maxval, plotBounds.bottom, plotBounds.top, clip:nil);
			var rely = y[i] - centery;
			var gap = (nextx - x[i]) * 0.1;
			if (rely < 0) {
				Pen.addRect(Rect(x[i] + gap, centery + rely, nextx- x[i] - (2 * gap), rely.abs))
			} {
				Pen.addRect(Rect(x[i] + gap, centery, nextx - x[i] - ( 2 * gap), rely))
			}
		}
	}

	// editing


	editDataIndex { |index, x, y, plotIndex|
		// WARNING: assuming index is in range!
		var val = this.getRelativePositionY(y);
		plotter.editFunc.value(plotter, plotIndex, index, val, x, y);
		value.put(index, val);
		valueCache = nil;
	}

	editData { |x, y, plotIndex|
		var index = this.getIndex(x);
		this.editDataIndex( index, x, y, plotIndex );
	}

	editDataLine { |pt1, pt2, plotIndex|
		var ptLeft, ptRight, ptLo, ptHi;
		var xSpec, ySpec;
		var i1, i2, iLo, iHi;
		var val;

		// get indexes related to ends of the line
		i1 = this.getIndex(pt1.x);
		i2 = this.getIndex(pt2.x);

		// if both ends at same index, simplify
		if( i1 == i2 ) {
			^this.editDataIndex( i2, pt2.x, pt2.y, plotIndex );
		};

		// order points and indexes
		if( i1 < i2 ) {
			iLo = i1; iHi = i2;
			ptLeft = pt1; ptRight = pt2;
		}{
			iLo = i2; iHi = i1;
			ptLeft = pt2; ptRight = pt1;
		};

		// if same value all over, simplify
		if( ptLeft.y == ptRight.y ) {
			val = this.getRelativePositionY(ptLeft.y);
			while( {iLo <= iHi} ) {
				value.put( iLo, val );
				iLo = iLo + 1;
			};
			// trigger once for second end of the line
			plotter.editFunc.value(plotter, plotIndex, i2, val, pt2.x, pt2.y);
			valueCache = nil;
			^this;
		};

		// get actual points corresponding to indexes
		xSpec = ControlSpec( ptLeft.x, ptRight.x );
		ySpec = ControlSpec( ptLeft.y, ptRight.y );
		ptLo = Point();
		ptHi = Point();
		ptLo.x = domainSpec.unmap(iLo) * plotBounds.width + plotBounds.left;
		ptHi.x = domainSpec.unmap(iHi) * plotBounds.width + plotBounds.left;
		ptLo.y = ySpec.map( xSpec.unmap(ptLo.x) );
		ptHi.y = ySpec.map( xSpec.unmap(ptHi.x) );

		// interpolate and store
		ySpec = ControlSpec( this.getRelativePositionY(ptLo.y), this.getRelativePositionY(ptHi.y) );
		xSpec = ControlSpec( iLo, iHi );
		while( {iLo <= iHi} ) {
			val = ySpec.map( xSpec.unmap(iLo) );
			value.put( iLo, val );
			iLo = iLo+1;
		};

		// trigger once for second end of the line
		plotter.editFunc.value(plotter, plotIndex, i2, val, pt2.x, pt2.y);
		valueCache = nil;
	}

	getRelativePositionX { |x|
		^domainSpec.map((x - plotBounds.left) / plotBounds.width)
	}

	getRelativePositionY { |y|
		^spec.map((plotBounds.bottom - y) / plotBounds.height)
	}

	hasSteplikeDisplay {
		^#[\levels, \steps, \bars].includesAny(plotMode)
	}

	needsPenFill { |pMode|
		^#[\bars].includes(pMode)
	}

	getIndex { |x|
		var ycoord = this.dataCoordinates;
		var xcoord = this.domainCoordinates(ycoord.size);
		var binwidth = 0;
		var offset;

		if (plotter.domain.notNil) {
			if (this.hasSteplikeDisplay) {
				// round down to index
				^plotter.domain.indexInBetween(this.getRelativePositionX(x)).floor.asInteger
			} {
				// round to nearest index
				^plotter.domain.indexIn(this.getRelativePositionX(x))
			};
		} {
			if (xcoord.size > 0) {
				binwidth = (xcoord[1] ?? {plotBounds.right}) - xcoord[0]
			};
			offset = if(this.hasSteplikeDisplay) { binwidth * 0.5 } { 0.0 };

			^(  // domain unspecified, values are evenly distributed between either side of the plot
				((x - offset - plotBounds.left) / plotBounds.width) * (value.size - 1)
			).round.clip(0, value.size-1).asInteger
		}
	}

	getDataPoint { |x|
		var index = this.getIndex(x).clip(0, value.size - 1);
		^[index, value.at(index)]
	}

	// settings

	zoomFont { |val|
		font = font.copy;
		font.size = max(1, font.size + val);
		this.font = font;
		drawGrid.clearCache;
	}
	copy {
		^super.copy.drawGrid_(drawGrid.copy)
	}

	prResampValues {
		var dataRes, numSpecSteps, specStep, sizem1;

		dataRes = plotBounds.width / max((value.size - 1), 1);

		^if (
			(dataRes >= plotter.resolution) or:
			{ plotter.domain.notNil } // don't resample if domain is specified
		) {
			value
		} {
			// resample
			if (valueCache.isNil or: { resolution != plotter.resolution }) {
				resolution = plotter.resolution;

				// domain is nil, so data fills full domain/view width
				numSpecSteps = (plotBounds.width / resolution).floor.asInteger;
				specStep = numSpecSteps.reciprocal;
				sizem1 = value.size - 1;

				valueCache = (numSpecSteps + 1).collect{ |i|
					value.blendAt((specStep * i) * sizem1)  // float index of new value
				}
			} {
				valueCache
			}
		}
	}

	prGetTickLabelSize { |drawGrid|
		var params, charCnt, labelSize;
		var gridEdges = if ( drawGrid.isKindOf(DrawGridY) ) {
			[drawGrid.bounds.top, drawGrid.bounds.bottom]
		} {
			[drawGrid.bounds.left, drawGrid.bounds.right]
		};

		params = drawGrid.grid.getParams(
			drawGrid.range[0], drawGrid.range[1], gridEdges[0], gridEdges[1]
		);
		charCnt = try {
			params.labels.flatten.collect(_.size).maxItem;
		} { 3 };
		labelSize = try {
			("0" ! charCnt).join.bounds(font).size
		} {
			("0" ! charCnt).join.bounds(Font(Font.defaultSansFace, 9)).size
		};
		labelSize.width = labelSize.width * 1.1;

		^[labelSize, params.labels]
	}
}



Plotter {

	var <>name, <>bounds, <>parent;
	var <value, <data, <domain;
	var <plots, <specs, <domainSpecs, plotColor, labelX, labelY;
	var <cursorPos, plotMode, <>editMode = false, <>normalized = false;
	var <>resolution = 1, <>findSpecs = true, <superpose = false;
	var modes, <interactionView;
	var <editPlotIndex, <editPos;

	var <>drawFunc, <>editFunc;

	*new { |name, bounds, parent|
		^super.newCopyArgs(name).makeWindow(parent, bounds)
	}

	makeWindow { |argParent, argBounds|
		var btnBounds;
		parent = argParent ? parent;
		bounds = argBounds ? bounds;
		if(parent.isNil) {
			parent = Window.new(name ? "Plot", bounds ? Rect(100, 200, 400, 300));
			bounds = parent.view.bounds.insetBy(8);
			parent.background_(QtGUI.palette.window);
			interactionView = UserView.new(parent, bounds);

			interactionView.drawFunc = { this.draw };
			parent.front;
			parent.onClose = { parent = nil };

		} {
			bounds = bounds ?? { parent.bounds.moveTo(0, 0) };
			interactionView = UserView.new(parent, bounds);
			interactionView.drawFunc = { this.draw };
		};
		modes = [\points, \levels, \linear, \plines, \steps, \bars].iter.loop;
		this.plotMode = \linear;
		this.plotColor = GUI.skins.plot.plotColor;

		interactionView
		.background_(Color.clear)
		.focusColor_(Color.clear)
		.resize_(5)
		.focus(true)
		.mouseDownAction_({ |v, x, y, modifiers|
			cursorPos = x @ y;
			if(superpose.not) {
				editPlotIndex = this.pointIsInWhichPlot(cursorPos);
				editPlotIndex !? {
					editPos = x @ y; // new Point instead of cursorPos!
					if(editMode) {
						plots.at(editPlotIndex).editData(x, y, editPlotIndex);
						if(this.numFrames < 200) { this.refresh };
					};
				}
			};
			if(modifiers.isAlt) { this.postCurrentValue(x, y) };
		})
		.mouseMoveAction_({ |v, x, y, modifiers|
			cursorPos = x @ y;
			if(superpose.not && editPlotIndex.notNil) {
				if(editMode) {
					plots.at(editPlotIndex).editDataLine(editPos, cursorPos, editPlotIndex);
					if(this.numFrames < 200) { this.refresh };
				};
				editPos = x @ y;  // new Point instead of cursorPos!

			};
			if(modifiers.isAlt) { this.postCurrentValue(x, y) };
		})
		.mouseUpAction_({
			cursorPos = nil;
			editPlotIndex = nil;
			if(editMode && superpose.not) { this.refresh };
		})
		.keyDownAction_({ |view, char, modifiers, unicode, keycode|
			if(modifiers.isCmd.not) {
				switch(char,
					// y zoom out / font zoom
					$-, {
						if(modifiers.isCtrl) {
							plots.do(_.zoomFont(-2));
						} {
							this.specs = specs.collect(_.zoom(7/5));
							normalized = false;
						}
					},
					// y zoom in / font zoom
					$+, {
						if(modifiers.isCtrl) {
							plots.do(_.zoomFont(2));
						} {
							this.specs = specs.collect(_.zoom(5/7));
							normalized = false;
						}
					},
					// compare plots
					$=, {
						this.calcSpecs(separately: false);
						this.updatePlotSpecs;
						normalized = false;
					},

					/*// x zoom out (doesn't work yet)
					$*, {
					this.domainSpecs = domainSpecs.collect(_.zoom(3/2));
					},
					// x zoom in (doesn't work yet)
					$_, {
					this.domainSpecs = domainSpecs.collect(_.zoom(2/3))
					},*/

					// normalize

					$n, {
						if(normalized) {
							this.specs = specs.collect(_.normalize)
						} {
							this.calcSpecs;
							this.updatePlotSpecs;
						};
						normalized = normalized.not;
					},

					// toggle grid
					$g, {
						plots.do { |x| x.gridOnY = x.gridOnY.not }
					},
					// toggle domain grid
					$G, {
						plots.do { |x| x.gridOnX = x.gridOnX.not };
					},
					// toggle plot mode
					$m, {
						this.plotMode = modes.next;
					},
					// toggle editing
					$e, {
						editMode = editMode.not;
						"plot edit mode %\n".postf(if(editMode) { "on" } { "off" });
					},
					// toggle superposition
					$s, {
						this.superpose = this.superpose.not;
					},
					// print
					$p, {
						this.print
					}
				);
				parent.refresh;
			};
		});
	}

	value_ { |arrays|
		this.setValue(arrays, findSpecs, true)
	}

	setValue { |arrays, findSpecs = true, refresh = true, separately = true, minval, maxval, defaultRange|
		value = arrays;
		domain = nil;  // for now require user to re-set domain after setting new value
		data = this.prReshape(arrays);
		if(findSpecs) {
			this.calcDomainSpecs;
			this.calcSpecs(separately, minval, maxval, defaultRange);
		};
		this.updatePlots;
		this.updatePlotBounds;
		if(refresh) { this.refresh };
	}

	// TODO: currently domain is constrained to being identical across all channels
	// domain values are (un)mapped within the domainSpec
	domain_ { |domainArray|
		var dataSize, sizes;

		domainArray ?? {
			domain = nil;
			^this
		};

		dataSize = if (this.value.rank > 1) {
			sizes = this.value.collect(_.size);
			if (sizes.any(_ != this.value[0].size)) {
				Error(format(
					"[Plotter:-domain_] Setting the domain values isn't supported "
					"for multichannel data of different sizes %.", sizes
				)).throw;
			};
			this.value[0].size;
		} {
			this.value.size
		};


		if (domainArray.size != dataSize) {
			Error(format(
				"[Plotter:-domain_] Domain array size [%] does not "
				"match data array size [%]", domainArray.size, dataSize
			)).throw;
		} {
			domain = domainArray
		}
	}

	superpose_ { |flag|
		var dom, domSpecs;
		dom = domain.copy;
		domSpecs = domainSpecs.copy;

		superpose = flag;
		if ( value.notNil ){
			this.setValue(value, false, false);
		};

		// for individual plots, restore previous domain state
		this.domain_(dom);
		if (superpose.not) {
			this.domainSpecs_(domSpecs);
		};
		this.labelX !? { this.labelX_(this.labelX) };
		this.labelY !? { this.labelY_(this.labelY) };
		this.refresh;
	}

	numChannels {
		^value.size
	}

	numFrames {
		if(value.isNil) { ^0 };
		^value.first.size
	}

	draw {
		var b = this.interactionView.bounds;
		if(b != bounds) {
			bounds = b;
			this.updatePlotBounds;
		};
		Pen.use {
			plots.do { |plot| plot.draw };
		}
	}

	drawBounds {
		^bounds.moveTo(0, 0);
	}

	print {
		var printer = QPenPrinter.new;
		printer.showDialog {
			printer.print { this.draw }
		} { "printing canceled".postln }
	}

	// subviews

	updatePlotBounds {
		var bounds = this.drawBounds;
		var deltaY = if(data.size > 1 ) { 4.0 } { 0.0 };
		var distY = bounds.height / data.size;
		var height = distY - deltaY;

		plots.do { |plot, i|
			plot.bounds_(
				Rect(bounds.left, distY * i + bounds.top, bounds.width, height)
			)
		};
		this.refresh;
	}

	makePlots {
		var template = if(plots.isNil) { Plot(this) } { plots.last };
		plots !? { plots = plots.keep(data.size.neg) };
		plots = plots ++ template.dup(data.size - plots.size);
		plots.do { |plot, i| plot.value = data.at(i) };
		this.plotColor_(plotColor);
		this.plotMode_(plotMode);
		this.updatePlotSpecs;
	}

	updatePlots {
		if(plots.size != data.size) {
			this.makePlots;
		} {
			plots.do { |plot, i|
				plot.value = data.at(i)
			}
		}
	}

	updatePlotSpecs {
		var template, smin, smax;

		specs !? {
			if (superpose) {
				// NOTE: for superpose, all spec properties except
				// minval and maxval are inherited from first spec
				template = specs[0].copy;
				smin = specs.collect(_.minval).minItem;
				smax = specs.collect(_.maxval).maxItem;
				plots[0].spec = template.minval_(smin).maxval_(smax);
			} {
				plots.do { |plot, i|
					plot.spec = specs.clipAt(i)
				}
			}
		};

		domainSpecs !? {
			if (superpose) {
				template = domainSpecs[0].copy;
				smin = domainSpecs.collect(_.minval).minItem;
				smax = domainSpecs.collect(_.maxval).maxItem;
				plots[0].domainSpec = template.minval_(smin).maxval_(smax);
			} {
				plots.do { |plot, i|
					plot.domainSpec = domainSpecs.clipAt(i)
				}
			}
		};

		this.updatePlotBounds;
	}

	setProperties { |... pairs|
		pairs.pairsDo { |selector, value|
			selector = selector.asSetter;
			plots.do { |x| x.perform(selector, value) }
		};
		this.updatePlotBounds;
	}

	plotColor_ { |colors|
		plotColor = colors.as(Array);
		plots.do { |plt, i|
			// rotate colors to ensure proper behavior with superpose
			// (so this Plot's color is first in its color array)
			plt.plotColor_(plotColor.rotate(i.neg))
		}
	}

	plotColor {
		var first = plotColor.first;
		^if (plotColor.every(_ == first)) { first } { plotColor };
	}

	plotMode_ { |modes|
		plotMode = modes.asArray;
		plots.do { |plt, i|
			// rotate to ensure proper behavior with superpose
			plt.plotMode_(plotMode.rotate(i.neg))
		}
	}

	plotMode {
		var first = plotMode.first;
		^if (plotMode.every(_ == first)) { first } { plotMode };
	}

	labelX_ { |labels|
		labelX = if(labels.isKindOf(Array)) {
			labels.collect(_ !? (_.asString))
		} { [ labels !? (_.asString) ] };
		plots.do { |plt, i|
			// rotate to ensure proper behavior with superpose
			plt.labelX_(labelX.rotate(i.neg)[0])
		};
		this.updatePlotBounds;
	}

	labelX {
		^ labelX !? {
			if (labelX.every(_ == labelX.first)) { labelX.first } { labelX }
		};
	}

	labelY_ { |labels|
		labelY = if(labels.isKindOf(Array)) {
			labels.collect(_ !? (_.asString))
		} { [ labels !? (_.asString) ] };
		plots.do { |plt, i|
			// rotate to ensure proper behavior with superpose
			plt.labelY_(labelY.rotate(i.neg)[0])
		};
		this.updatePlotBounds;
	}

	labelY {
		^ labelY !? {
			if (labelY.every(_ == labelY.first)) { labelY.first } { labelY };
		};
	}

	// specs

	specs_ { |argSpecs|
		specs = argSpecs.asArray.clipExtend(data.size).collect(_.asSpec);
		this.updatePlotSpecs;
	}

	domainSpecs_ { |argSpecs|
		domainSpecs = argSpecs.asArray.clipExtend(data.size).collect(_.asSpec);
		this.updatePlotSpecs;
	}

	minval_ { |val|
		val = val.asArray;
		specs.do { |x, i| x.minval = val.wrapAt(i) };
		this.updatePlotSpecs;
	}

	maxval_ { |val|
		val = val.asArray;
		specs.do { |x, i| x.maxval = val.wrapAt(i) };
		this.updatePlotSpecs;
	}


	calcSpecs { |separately = true, minval, maxval, defaultRange|
		var ranges = [minval, maxval].flop;
		var newSpecs = ranges.collect(_.asSpec).clipExtend(data.size);
		if(separately) {
			newSpecs = newSpecs.collect { |spec, i|
				var list = data.at(i);
				if(list.notNil) {
					spec = spec.looseRange(list, defaultRange, *ranges.wrapAt(i));
				} {
					spec
				};
			}
		} {
			newSpecs = newSpecs.first.looseRange(data.flat, defaultRange, *ranges.at(0));
		};
		this.specs = newSpecs;
	}

	calcDomainSpecs {
		// for now, a simple version
		domainSpecs = data.collect { |val|
			[0, val.size - 1, \lin].asSpec
		}
	}


	// interaction
	pointIsInWhichPlot { |point|
		var res;
		if(plots.isNil) { ^nil };
		res = plots.detectIndex { |plot|
			point.y.exclusivelyBetween(plot.bounds.top, plot.bounds.bottom)
		};
		^res ?? {
			if(point.y < bounds.center.y) { 0 } { plots.size - 1 }
		}
	}

	getDataPoint { |x, y|
		var plotIndex = this.pointIsInWhichPlot(x @ y);
		^plotIndex !? {
			plots.at(plotIndex).getDataPoint(x)
		}
	}

	postCurrentValue { |x, y|
		this.getDataPoint(x, y).postln
	}

	editData { |x, y|
		var plotIndex = this.pointIsInWhichPlot(x @ y);
		plotIndex !? {
			plots.at(plotIndex).editData(x, y, plotIndex);
		};
	}

	refresh {
		parent !? { parent.refresh }
	}

	prReshape { |item|
		var size, array = item.asArray;
		if(item.first.isSequenceableCollection.not) {
			^array.bubble;
		};
		if(superpose) {
			if(array.first.first.isSequenceableCollection) { ^array };
			size = array.maxItem { |x| x.size }.size;
			// for now, just extend data:
			^array.collect { |x| x.asArray.clipExtend(size) }.flop.bubble };
		^array
	}
}


+ ArrayedCollection {
	plot { |name, bounds, discrete = false, numChannels, minval, maxval, separately = true|
		var array, plotter;
		array = this.as(Array);

		if(array.maxDepth > 3) {
			"Cannot currently plot an array with more than 3 dimensions".warn;
			^nil
		};
		plotter = Plotter(name, bounds);
		if(discrete) { plotter.plotMode = \points };

		numChannels !? { array = array.unlace(numChannels) };
		array = array.collect {|elem, i|
			if (elem.isKindOf(Env)) {
				elem.asMultichannelSignal.flop
			} {
				if(elem.isNil) {
					Error("cannot plot array: non-numeric value at index %".format(i)).throw
				};
				elem
			}
		};

		plotter.setValue(
			array,
			findSpecs: true,
			refresh: true,
			separately: separately,
			minval: minval,
			maxval: maxval
		);

		^plotter
	}
}

+ Collection {
	plotHisto { arg steps = 100, min, max;
		var histo = this.histo(steps, min, max);
		var plotter = histo.plot;
		var minmax = [min ?? { this.minItem }, max ?? { this.maxItem }];
		var binwidth = minmax[1] - minmax[0] / steps;

		^plotter
		.domainSpecs_([minmax.asSpec])
		.specs_([[0, histo.maxItem * 1.05, \linear, 1].asSpec])
		.plotMode_(\steps)
		.labelY_("Occurrences")
		.labelX_("Bins")
		.domain_(binwidth * (0..steps-1) + minmax[0])
		;
	}
}


+ Function {
	plot { |duration = 0.01, target, bounds, minval, maxval, separately = false|
		var server, plotter, action;
		var name = this.asCompileString;

		if(name.size > 50 or: { name.includes(Char.nl) }) { name = "function plot" };

		plotter = Plotter(name, bounds);
		// init data in case function data is delayed (e.g. server booting)
		plotter.value = [0.0];

		target = target.asTarget;
		server = target.server;
		action = { |array, buf|
			var numChan = buf.numChannels;
			var numFrames = buf.numFrames;
			var frameDur;

			defer {
				plotter.setValue(
					array.unlace(numChan),
					findSpecs: true,
					refresh: false,
					separately: separately,
					minval: minval,
					maxval: maxval
				);

				plotter.domainSpecs = ControlSpec(0, duration, units: "Time (s)");

				// If individual values might be separated by at least 2.5 px
				// (based on a plot at full screen width), set the x values (domain)
				// explicitly for accurate time alignment with grid lines.
				if(numFrames < (Window.screenBounds.width / 2.5)) {
					frameDur = if(this.value.rate == \control) {
						server.options.blockSize / server.sampleRate
					} {
						1 / server.sampleRate
					};
					plotter.domain = numFrames.collect(_ * frameDur);
				};
			};
		};

		if(server.isLocal) {
			this.loadToFloatArray(duration, target, action: action)
		} {
			this.getToFloatArray(duration, target, action: action)
		};

		^plotter
	}

	plotAudio { |duration = 0.01, minval = -1, maxval = 1, server, bounds|
		^this.plot(duration, server, bounds, minval, maxval)
	}
}

+ Bus {
	plot { |duration = 0.01, bounds, minval, maxval, separately = false|
		if (this.rate == \audio, {
			^{ InFeedback.ar(this.index, this.numChannels) }.plot(duration, this.server, bounds, minval, maxval, separately);
		},{
			^{ In.kr(this.index, this.numChannels) }.plot(duration, this.server, bounds, minval, maxval, separately);
		});
	}

	plotAudio { |duration = 0.01, minval = -1, maxval = 1, bounds|
		^this.plot(duration, bounds, minval, maxval)
	}
}

+ Wavetable {
	plot { |name, bounds, minval, maxval|
		^this.asSignal.plot(name, bounds, minval: minval, maxval: maxval)
	}
}

+ Buffer {
	plot { |name, bounds, minval, maxval, separately = false|
		var plotter, action;
		if(server.serverRunning.not) { "Server % not running".format(server).warn; ^nil };
		if(numFrames.isNil) { "Buffer not allocated, can't plot data".warn; ^nil };

		plotter = [0].plot(
			name ? "Buffer plot (bufnum: %)".format(this.bufnum),
			bounds, minval: minval, maxval: maxval
		);

		action = { |array, buf|
			{
				plotter.setValue(
					array.unlace(buf.numChannels),
					findSpecs: true,
					refresh: false,
					separately: separately,
					minval: minval,
					maxval: maxval
				);
				plotter.domainSpecs = ControlSpec(
					0.0, buf.numFrames,
					units: if (buf.numChannels == 1) { "Samples" } { "Frames" }
				);
			}.defer
		};

		if(server.isLocal) {
			this.loadToFloatArray(action:action)
		} {
			this.getToFloatArray(action:action)
		};

		^plotter
	}
}


+ Env {
	plot { |size = 400, bounds, minval, maxval, name|
		var plotLabel = if (name.isNil) { "Envelope plot" } { name };
		var plotter = [this.asMultichannelSignal(size).flop]
		.plot(name, bounds, minval: minval, maxval: maxval);

		var duration     = this.duration.asArray;
		var channelCount = duration.size;

		var totalDuration = if (channelCount == 1) { duration } { duration.maxItem ! channelCount };

		plotter.domainSpecs = totalDuration.collect(ControlSpec(0, _));
		plotter.refresh;
		^plotter
	}
}

+ AbstractFunction {
	plotGraph { arg n=500, from = 0.0, to = 1.0, name, bounds, discrete = false,
		numChannels, minval, maxval, separately = true;
		var array = Array.interpolation(n, from, to);
		var res = array.collect { |x| this.value(x) };
		^res.plot(name, bounds, discrete, numChannels, minval, maxval, separately)
	}
}
