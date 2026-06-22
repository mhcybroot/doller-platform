import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;
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
  bool _downloading = false;

  Future<void> _share() async {
    setState(() => _sharing = true);
    try {
      await Share.shareXFiles(
        [XFile(widget.filePath)],
        fileNameOverrides: [widget.fileName],
      );
    } on UnimplementedError {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Sharing not supported on this platform. File located at:\n${widget.filePath}'),
            duration: const Duration(seconds: 5),
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error sharing file: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _sharing = false);
      }
    }
  }

  Future<void> _download() async {
    setState(() => _downloading = true);
    try {
      final downloadsDir = await getDownloadsDirectory();
      if (downloadsDir != null) {
        final savePath = p.join(downloadsDir.path, widget.fileName);
        await File(widget.filePath).copy(savePath);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Saved to: $savePath')),
          );
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Could not access Downloads directory')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error saving file: $e')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _downloading = false);
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
            onPressed: _downloading ? null : _download,
            icon: const Icon(Icons.download_outlined),
            tooltip: 'Download PDF',
          ),
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
