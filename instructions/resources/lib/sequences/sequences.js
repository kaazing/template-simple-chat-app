// Sequences
//
// Author:    Robin Zimmermann
// Version:   1.2
// Date:      October 2016

// <seq name="section1" prefix="Section " suffix="." ref="deployment" current="true" resetSeq="h1">#SEQ#</seq>
// Optional: prefix, suffix, ref
//
// <seq-ref name="section1" ref="deployment" prefix="Step " link="true">#SEQREF#</seq-ref>
// Optional: prefix, link
//

var sequences = [];

var errorCount;

// Remove all child elements of the given element.
//
var removeChildren = function(element) {
	while (element.hasChildNodes()) {
        element.removeChild(element.lastChild);
    }
}

// Display an message when a reference has an error.
//
// element
// The seq-ref element that had the error.
//
// errorStr
// The error message to display.
//
var handleRefError = function(element, errorStr) {
	// Create a <span> with the error message.
	//
	var spanEl = document.createElement("span");
	spanEl.setAttribute("style", "color: red; font-weight: bold; background-color: yellow;");
	var errorEl = document.createTextNode(errorStr);
	spanEl.appendChild(errorEl);

	// Add the span to the seq-ref element.
	//
    element.insertBefore(spanEl, element.firstChild);
}

// Initialise references to sequences.
//
var initReferences = function() {
	// Loop over every sequence reference declaration.
	//
    $.each($("seq-ref"), function(index, value) {

		removeChildren(value);

        var seqName = value.attributes.name.value;
		var refName = value.getAttribute("ref");
		var linkFlag = value.getAttribute("link");
		var prefix = value.getAttribute("prefix");

		// Validate the sequence exists.
		//
		var seq = sequences[seqName];
		if (typeof seq == "undefined") {
			errorCount++;
			handleRefError(value, "Invalid sequence name in reference: "+seqName);
			return;
		}

		// Validate the reference exists.
		//
		var ref = seq.references[refName];
		if (typeof ref == "undefined") {
			errorCount++;
			handleRefError(value, "Invalid reference name: "+refName);
			return;
		}

		var num = ref.num;
		var anchorName = ref.anchorName;

		var val = num;
		if (prefix != null) {
			val = prefix + val;
		}
		var valEl = document.createTextNode(val);

		if (linkFlag != null) {
			var anchorEl = document.createElement("a");
			anchorEl.setAttribute("href", "#"+anchorName);
			anchorEl.appendChild(valEl);
			valEl = anchorEl
		}

        value.insertBefore(valEl, value.firstChild);

    });
}

// Initialiase the sequeuences.
//
var initSequences = function() {

	errorCount = 0;

	// Loop over every sequence declaration.
	//
    $.each($("seq"), function(index, value) {

		removeChildren(value);

		// Get the sequence name.
		//
		var seqName = value.attributes.name.value;

		// Check if we already have this sequeunce or if it's a new one.
		//
		var seq = sequences[seqName];
		if (typeof seq == "undefined") {
			seq = {"nextNum": 1, "references": [], "resetList": []};
			sequences[seqName] = seq;
		}

        // Add an anchor for each sequence instance.
		//
        var anchorName = seqName+"-"+seq.nextNum;
		var anchorEl = document.createElement("a");
		anchorEl.setAttribute("name", anchorName);
		value.parentNode.insertBefore(anchorEl, value);

		// If this sequence instance has a reference then store it.
		//
        var ref = value.getAttribute("ref");
		if (ref != null) {
			// Make sure this reference hasn't been used before. They need to be unique within
			// a sequence.
			//
			var r = seq.references[ref];
			if (typeof r ==  "undefined") {
        	    seq.references[ref] = { "num": seq.nextNum, "anchorName": anchorName};
			} else {
    			errorCount++;
    			handleRefError(value, "Reference name already in use: "+ref);
			}
		}

        // If this sequence has reset sequence, then store a reference to this one in the reset sequence.
		//
        var resetSeq = value.getAttribute("resetSeq");
		if (resetSeq != null) {
			var parent = sequences[resetSeq];
			if (typeof parent ==  "undefined") {
    			errorCount++;
    			handleRefError(value, "This sequence references a reset sequence which doesn't yet exist: "+resetSeq);
			} else {
				addReset(parent, seqName);
			}
		}

        // Build up the value for this sequence. Assign the next value to this sequence instance
		// and increment the value.
		//
		var seqVal;

		// Check if the current flag is set.
		//
		var currentFlag = value.getAttribute("current");
		if (currentFlag == undefined) {

		    // Reset any reset sequences.
		    //
            for (i=0; i<seq.resetList.length; i++) {
		        var resetSeqName = seq.resetList[i];
                sequences[resetSeqName].nextNum = 1;
            }

		    seqVal = seq.nextNum++;
		} else if (currentFlag === "true" ) {
	  	    seqVal = seq.nextNum-1;
	    } else {
			errorCount++;
		    handleRefError(value, "If current attribute is present, it must be set to \"true\".");
		}

		// If there is a prefix specified, add it.
		//
        var prefix = value.getAttribute("prefix");
		if (prefix != null) {
			seqVal = prefix + seqVal;
		}

		// If there is a suffix specified, add it.
		//
        var suffix = value.getAttribute("suffix");
		if (suffix != null) {
			seqVal += suffix;
		}

		// Add the sequence value to the DOM.
		//
		var seqValEl = document.createTextNode(seqVal);
		value.insertBefore(seqValEl, value.firstChild);

    });

    initReferences();

	if (errorCount > 0) {
		alert("<seq>/<seq-ref>: There are "+errorCount+" errors.");
	}
}

var addReset = function(parent, childName) {
  for (i=0; i<parent.resetList.length; i++) {
	  return;
  }
  var index = parent.resetList.length;
  parent.resetList[index] = childName;
}

$(document).ready(function() {
	initSequences();
});
