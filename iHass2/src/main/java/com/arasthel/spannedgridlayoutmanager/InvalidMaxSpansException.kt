package com.arasthel.spannedgridlayoutmanager

class InvalidMaxSpansException(maxSpanSize: Int) :
        RuntimeException("Invalid layout spans: $maxSpanSize. Span size must be at least 1.")