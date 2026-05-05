import 'dart:io';

import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import 'package:syncfusion_flutter_pdfviewer/pdfviewer.dart';

class CustomEntriesPdfPreviewPage extends StatefulWidget {
  const CustomEntriesPdfPreviewPage({
    super.key,
    required this.filePath,
    required this.fileName,
  });

  final String filePath;
  final String fileName;

  @override
  State<CustomEntriesPdfPreviewPage> createState() =>
      _CustomEntriesPdfPreviewPageState();
}

class _CustomEntriesPdfPreviewPageState
    extends State<CustomEntriesPdfPreviewPage> {
  bool _sharing = false;

  Future<void> _share() async {
    setState(() => _sharing = true);
    try {
      await Share.shareXFiles(
        [XFile(widget.filePath)],
        fileNameOverrides: [widget.fileName],
      );
    } finally {
      if (mounted) {
        setState(() => _sharing = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.fileName),
        actions: [
          IconButton(
            onPressed: _sharing ? null : _share,
            icon: const Icon(Icons.share_outlined),
            tooltip: 'Share PDF',
          ),
        ],
      ),
      body: SfPdfViewer.file(
        File(widget.filePath),
      ),
    );
  }
}
