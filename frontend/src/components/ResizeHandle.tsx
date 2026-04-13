import React from 'react';

interface ResizeHandleProps {
  className?: string;
  onPointerDown?: React.PointerEventHandler<HTMLDivElement>;
}

const ResizeHandle: React.FC<ResizeHandleProps> = ({ className = '', onPointerDown }) => {
  return (
    <div className={`native-resize-handle ${className}`} onPointerDown={onPointerDown} role="separator" aria-orientation="vertical">
      <div className="resize-grip" />
      <div className="resize-grip" />
      <div className="resize-grip" />
    </div>
  );
};

export default ResizeHandle;

